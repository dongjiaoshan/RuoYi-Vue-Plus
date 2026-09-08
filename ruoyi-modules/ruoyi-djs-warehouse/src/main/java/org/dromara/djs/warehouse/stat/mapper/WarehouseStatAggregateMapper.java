package org.dromara.djs.warehouse.stat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 仓库统计聚合查询 Mapper（WMS-STAT-001，over 各源表，compute-then-store）。
 *
 * <p>每个 @Select 返回某自然日（或月）的单指标聚合值，service 端组装成日表/作物日表/月表行。
 * 所有 @Select 含聚合，WHERE 显式带 {@code tenant_id}（多租户拦截器对自定义聚合不保证注入）。
 * 日期列均为 DATETIME，按 {@code DATE(col) = #{statDate}} 截到自然日。</p>
 *
 * <h3>源表 → 指标映射</h3>
 * <ul>
 *   <li>{@code t_warehouse_bar_info} + {@code t_warehouse_outsource_pig}：猪只段指标，按<b>三组 cohort</b>
 *       分别分桶（同一指标的分子分母必须来自同一批猪）——
 *       送宰 cohort（{@code marketing_time} / {@code slaughter_date}）→ 屠宰头数 / 送宰总重 / 送宰均重；
 *       称重 cohort（{@code arrive_time}）→ 接收重量 / 屠宰率 / 白条出品率的分母；
 *       处理完成 cohort（{@code finish_time}）→ 处理完成头数 / 其接收重量之和（两个诊断列）</li>
 *   <li>{@code t_warehouse_stock_flow} 入库方向 × {@code belong_type='white_bar'}：白条总重 / 白条均重
 *       的分母（猪只去重数），按 {@code flow_date} 分桶 —— 「当日入白条库的半扇 + 整只」</li>
 *   <li>{@code t_warehouse_pig_cut_record}：分割白条数 / 分割白条总重（pickup_time + pickup_weight）</li>
 *   <li>{@code t_warehouse_stock_flow} {@code flow_type='cut_out_in'}：分割产品总重（flow_date）</li>
 *   <li>{@code t_warehouse_loss_flow}：所有损耗按 loss_type 取（防重复计，loss_date）</li>
 *   <li>{@code t_warehouse_vegetable_handle}：毛菜称量 / 发往月台（picked / send_platform，pick_start_time）</li>
 *   <li>{@code t_warehouse_handle_record}：作物维度采摘 / 发往月台（按 crop_id GROUP，handle_time）</li>
 *   <li>{@code t_warehouse_feed_log}：作物维度饲喂量（权威台账，毛菜间 + 仓库领用两路径，按 crop_id GROUP，feed_date）</li>
 *   <li>{@code t_warehouse_veg_receive}：月台接收（receive_type=1 自产，receive_time）</li>
 *   <li>{@code t_warehouse_stock_flow}：生产领用 / 生产退回（prod_pick_out / prod_return_in，flow_date）</li>
 *   <li>{@code t_warehouse_loss_flow}：作物维度损耗（veg_handle_loss 经 plot→crop；production_loss/manual_loss 经 product_id→crop.related_product）</li>
 * </ul>
 *
 * @author djs
 * @since WMS-STAT-001
 */
@Mapper
public interface WarehouseStatAggregateMapper {

    // ============================================================
    //  仓库日表（row16）源聚合
    // ============================================================

    /**
     * 屠宰头数：<b>当日送宰</b>的猪只头数（送宰 cohort），自养 + 外购一起算。
     *
     * <p>本表统计的是<b>送宰</b>，不是出栏——出栏头数在养殖模块统计。自养猪没有独立的送宰时间字段：
     * 养殖侧出栏事件（{@code PigMarketingEventListener#onPigMarketing}）写 {@code marketing_time}
     * 那一刻就是交宰时刻、bar_info 这一行本身就是送宰记录，故以 {@code marketing_time} 作送宰锚点。</p>
     *
     * <p>自养走 {@code t_warehouse_bar_info}（一行 = 一头整猪 = 一个耳号，按 {@code marketing_time} 分桶，
     * {@code buy_date IS NULL} 排除外购镜像行）；外购生猪走 {@code t_warehouse_outsource_pig}
     * 按送宰日 {@code slaughter_date} 分桶，与自养<b>相加</b>（客户口径：外购的也计送宰头数）。
     * 两者不会重复计——外购生猪录入时会往 bar_info 镜像一行带 {@code buy_date}，恰好被自养侧的
     * {@code buy_date IS NULL} 挡掉，同一头外购猪全表只计一次。</p>
     *
     * <p>头数<b>不</b>按燎毛间称重时刻算：送宰与称重经常跨天（staging 实测有送宰 08-02、称重 08-04 的猪），
     * 按称重算会让「当日送宰头数」跟着下游工序漂。同理不走 {@code t_warehouse_pig_burn_record}——
     * 它是「产出行」粒度（一头拆两个半只/猪头/猪蹄逐条录入 → 多行），一头整猪会被计成 N 头
     * （row198 客户实证「一头计 4」）。</p>
     */
    @Select("""
        SELECT (SELECT COUNT(*) FROM t_warehouse_bar_info
                 WHERE del_flag = '0' AND tenant_id = #{tenantId}
                   AND buy_date IS NULL AND DATE(marketing_time) = #{statDate})
             + (SELECT COUNT(*) FROM t_warehouse_outsource_pig
                 WHERE del_flag = '0' AND tenant_id = #{tenantId}
                   AND DATE(slaughter_date) = #{statDate})
        """)
    int countSlaughter(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 接收重量：<b>当日在燎毛间完成称重</b>的猪只总重 = Σ 到场重 {@code arrive_weight}（称重 cohort）。
     *
     * <p>源 {@code t_warehouse_bar_info}（一行 = 一头整猪），过滤 {@code in_method=1}（燎毛间）+
     * {@code arrive_weight} 非空（已过磅）。分桶键 {@code arrive_time} = weighBurn 里跟 arrive_weight
     * 同一次过磅写入、且只写第一次的不可变锚，正是「完成称重那一刻」。{@code COALESCE} 退到
     * {@code in_time} 只兜 arrive_time 补列之前的老数据（staging 有 1 头）。</p>
     *
     * <p>一头一行、天然不重复。<b>不能</b>走 burn_record {@code GROUP BY COALESCE(ear_no, burn_id)}：
     * 外购猪 ear_no 空退 burn_id、同一头拆多条产出行 → arrive_weight 被按产出行重复累加偏大
     * （row199 客户实证）。</p>
     */
    @Select("""
        SELECT COALESCE(SUM(arrive_weight), 0) FROM t_warehouse_bar_info
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND in_method = 1 AND arrive_weight IS NOT NULL
          AND DATE(COALESCE(arrive_time, in_time)) = #{statDate}
        """)
    BigDecimal sumArriveWeight(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 屠宰率的分子分母（称重 cohort 里<b>有出栏重量</b>的子集，一起取保证同一批猪）。
     *
     * <p>分子 {@code rateArrive} = Σ 到场重；分母 {@code rateBase} = Σ 出栏重量
     * （自养取 {@code bar.marketing_weight}；外购生猪取 {@code outsource_pig.pig_weight}，按
     * {@code bar_id} 反查——外购录入时把生成的 bar_id 回写到了外购台账）。</p>
     *
     * <p>出栏重量取不到的猪（自养漏录出栏重 / 外购镜像行找不到台账）从分子分母<b>同时</b>剔除，
     * 不让它单边压低比率；它的到场重仍计在 {@link #sumArriveWeight} 的接收重量里，两者刻意不等。</p>
     *
     * <p>外购侧用相关子查询而非 JOIN：{@code outsource_pig.bar_id} 无唯一约束，JOIN 撞到重复台账行
     * 会让同一头猪的 arrive_weight 被乘出多份。</p>
     *
     * @return 单行 {@code {rateArrive, rateBase}}
     */
    @Select("""
        SELECT COALESCE(SUM(t.arriveWeight), 0) AS rateArrive,
               COALESCE(SUM(t.baseWeight), 0)   AS rateBase
        FROM (
          SELECT b.arrive_weight AS arriveWeight,
                 CASE WHEN b.buy_date IS NULL THEN b.marketing_weight
                      ELSE (SELECT op.pig_weight FROM t_warehouse_outsource_pig op
                             WHERE op.bar_id = b.bar_id AND op.del_flag = '0'
                               AND op.tenant_id = #{tenantId}
                             ORDER BY (op.slaughter_date IS NULL), op.id LIMIT 1)
                 END AS baseWeight
          FROM t_warehouse_bar_info b
          WHERE b.del_flag = '0' AND b.tenant_id = #{tenantId}
            AND b.in_method = 1 AND b.arrive_weight IS NOT NULL
            AND DATE(COALESCE(b.arrive_time, b.in_time)) = #{statDate}
        ) t
        WHERE t.baseWeight IS NOT NULL
        """)
    Map<String, Object> selectSlaughterRateBase(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 白条段：<b>当日入白条库的白条产品</b>（半扇 / 整只）总重 + 这批白条对应的猪只去重数。
     *
     * <ul>
     *   <li>{@code barTotalWeight} = Σ 入库量 —— 白条总重展示列，同时是<b>白条出品率的分子</b>。</li>
     *   <li>{@code barPigCount} = 去重猪只数 —— 白条均重的分母。</li>
     * </ul>
     *
     * <h3>只算<b>燎毛产出</b>那一条入库通道（{@code flow_type = 'slaughter_burn'}）</h3>
     * <p>这是一次二选一：本指标取「白条<b>产出</b>」，<b>放弃</b>字面意义上的「任何入白条库」。</p>
     *
     * <p>🔴 <b>判据是正向枚举，不是「排除掉没有耳号的通道」。</b>唯一的产出通道是燎毛
     * （{@code slaughter_burn}），其余 IN 通道一律不计 —— <b>哪怕它带着真实耳号</b>。
     * 理由是语义而非可算性：白条总重同时是白条出品率的分子，衡量的是「这批猪屠宰出了多少白条」，
     * 只有燎毛那一刻是产出；货又回来了、盘出来了、从别的库挪过来了，都不是新产出，
     * 计入会让白条总重与出品率一起虚高。</p>
     *
     * <p>被排除的通道分两类，<b>两类的坏法不同，所以不能只靠「有没有耳号」去判</b>：</p>
     * <ul>
     *   <li><b>无耳号类</b>（{@code store_return_in} 门店退回 / {@code other} 期初 /
     *       {@code purchase_in} 采购入库 / {@code prod_return_in} 生产退回 /
     *       {@code third_phase_in} 三期入库 / {@code check_in} 盘盈）：staging 实测这几类 IN 流水
     *       {@code ear_no} 与 {@code white_bar_no} 100% 皆空。计入后重量进分子，却因
     *       {@code CONCAT('bar:', NULL) IS NULL} 一路 COALESCE 到底仍是 NULL、
     *       {@code COUNT(DISTINCT NULL) = 0} 而不给分母贡献任何一头猪 —— 极端情况某天只有这种行时
     *       {@code barPigCount = 0}，页面会出现「白条总重非 0、白条均重 0.00」的自相矛盾。</li>
     *   <li><b>有耳号类</b>（{@code transfer_in} 移库）：{@code LocationStockServiceImpl} 移库时
     *       把源库存行的 {@code ear_no} 与 {@code white_bar_no} <b>原样抄进入库流水</b>。
     *       半扇从猪肉鲜品库挪进冻品库，重量会再进一次分子，而那个耳号当天已经计过、分母纹丝不动
     *       —— 那头猪的白条均重直接翻倍，<b>而且没有任何可见异常</b>，比上一类更隐蔽。
     *       所以「这条通道有耳号 → 可以放回去」的推论是错的。</li>
     * </ul>
     *
     * <p>可达性不是假想：半扇确实会发到门店（{@code ship_out} + {@code stock_out_dest='ship_dock'}），
     * 而门店退回的品类白名单（{@code StoreReturnServiceImpl.PORK_BELONG_TYPES}）含 {@code white_bar}；
     * 移库对库位不设品类限制。今天两条都没炸只是因为退回产品字典里配的都是猪肉部位、恰好不含半扇，
     * 且 {@code transfer_in} 现有 0 行 —— 那是数据配置的偶然，不是代码约束。</p>
     *
     * <p>新增产出通道时（真加了，而不是把上面某条挪回来）改这里的白名单并同步契约测试；
     * 字典 {@code djs_flow_type} 里的 {@code bar_in_stock}「白条入库」当前<b>无任何代码写入</b>，
     * 是遗留字典项，不是漏掉的产出通道。</p>
     *
     * <h3>「半扇和整只」= {@code belong_type = 'white_bar'}（产品类别 = 白条产品）</h3>
     * <p>白条本体在产品档案里就是这个类别（{@code djs_belong_type} 的「白条产品」），燎毛间同批产出的
     * 猪头 / 猪脚 / 蹄髈是 {@code belong_type='pork'} 的副产、入的是猪肉鲜品库。判据挂在产品类别上而不是
     * 产品名，甲方在 admin 里新增一个白条产品（如「整只」）即自动计入，不必改代码；也不挂库位
     * ——{@code t_warehouse_location_info.location_type} 对所有仓库库位都是 {@code 'warehouse'}，
     * 「白条库」只有中文名可辨认，而库名可被后台改，不能当机器判据。</p>
     *
     * <h3>为什么读 {@code t_warehouse_stock_flow} 而不是 {@code t_warehouse_product_inhouse}</h3>
     * <p>{@code product_inhouse} 是<b>可变的在制品池</b>：白条被领用 / 打包时按实耗
     * {@code deductWeightById} 就地扣减、扣尽即软删，事后 SUM 会缩水，同一天重跑聚合会得到不同的数
     * （与 {@link #sumCutProductWeight} 不读该表是同一个理由）。燎毛入库流水
     * （{@code inout_type='IN'}）写入后只被「燎毛间产品重量调整」按新重量覆盖一次，下游一律另写出库行，
     * 是可复现的不可变账，重跑 / 补跑历史日得到的数一致。</p>
     *
     * <p>产品档案只按主键 + 租户联，<b>不</b>带 {@code p.del_flag='0'}：产品档案事后被停用 / 删除
     * 不该把历史入库抹掉。主键联表 1:1，不会放大行数。</p>
     *
     * <h3>猪只去重键</h3>
     * <p>自养猪按 {@code ear_no} 去重（一头猪出两扇 = 两行流水，同一耳号只计 1 头，正是甲方
     * 「猪只耳号数量（需要去重）」）。外购猪没有耳号（{@code t_warehouse_bar_info.ear_no} 恒 NULL，
     * 见 {@code OutsourcePigServiceImpl#createOutsourceBar}），故退到该产出行所属白条
     * {@code product_inhouse.white_bar_id}（= 一头猪）；再退到 {@code white_bar_no}（一扇）兜底。
     * 不能只写 {@code COUNT(DISTINCT ear_no)}：外购猪的重量会进分子却不进分母，白条均重被抬高。
     * {@code white_bar_id} 走相关子查询 + 定序 LIMIT 1，不用 JOIN —— JOIN 撞到重复行会让
     * {@code change_quantity} 被乘出多份，把分子做大。产出行事后被软删也照样能查到（不带
     * {@code del_flag} 条件），猪只身份不随在制品消耗而丢失。</p>
     *
     * @return 单行 {@code {barTotalWeight, barPigCount}}
     */
    @Select("""
        SELECT COALESCE(SUM(t.weight), 0) AS barTotalWeight,
               COUNT(DISTINCT t.pigKey)   AS barPigCount
        FROM (
          SELECT f.change_quantity AS weight,
                 COALESCE(f.ear_no,
                          CONCAT('bar:', (SELECT ih.white_bar_id
                                            FROM t_warehouse_product_inhouse ih
                                           WHERE ih.white_bar_no = f.white_bar_no
                                             AND ih.tenant_id = f.tenant_id
                                           ORDER BY ih.id LIMIT 1)),
                          CONCAT('half:', f.white_bar_no)) AS pigKey
          FROM t_warehouse_stock_flow f
          JOIN t_warehouse_product_info p
            ON p.id = f.product_id AND p.tenant_id = f.tenant_id
          WHERE f.del_flag = '0' AND f.tenant_id = #{tenantId}
            AND f.inout_type = 'IN'
            AND f.flow_type = 'slaughter_burn'
            AND p.belong_type = 'white_bar'
            AND DATE(f.flow_date) = #{statDate}
        ) t
        """)
    Map<String, Object> selectWhiteBarInAgg(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 处理完成 cohort（当日 {@code bar.finish_time} 落当天的那批猪，下称 F）的两个诊断量。
     *
     * <ul>
     *   <li>{@code finishedCount} = 当日处理完成头数（{@code t_warehouse_bar_info} 一行 = 一头猪）。</li>
     *   <li>{@code finishedArriveWeight} = Σ arrive_weight over F。</li>
     * </ul>
     *
     * <p>两个量只落盘、<b>不参与任何比率或均值</b>：白条总重 / 白条均重 / 白条出品率的分子分母全部由
     * {@link #selectWhiteBarInAgg}（当日入白条库口径）与 {@link #selectSlaughterRateBase}（Σ出栏重量）
     * 提供。它们记的是「这一天有几头猪走完了燎毛间」，与白条入库量是两件事（同一头猪可以在 A 日入库、
     * B 日才点处理完成）。</p>
     *
     * <p>{@code finish_time} 只在 finishBurn 的状态推进里写一次、之后不变，所以本聚合可复现；
     * 没进过燎毛间的白条永远 {@code finish_time IS NULL}，天然落不进任何一天。</p>
     *
     * @return 单行 {@code {finishedCount, finishedArriveWeight}}
     */
    @Select("""
        SELECT COUNT(*)                         AS finishedCount,
               COALESCE(SUM(b.arrive_weight), 0) AS finishedArriveWeight
        FROM t_warehouse_bar_info b
        WHERE b.del_flag = '0' AND b.tenant_id = #{tenantId}
          AND DATE(b.finish_time) = #{statDate}
        """)
    Map<String, Object> selectFinishedAgg(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /** 送宰总重(自产)：当日送宰的自养猪总重 = Σ bar.marketing_weight（送宰 cohort；外购镜像行 buy_date 非空，排除）。 */
    @Select("""
        SELECT COALESCE(SUM(marketing_weight), 0) FROM t_warehouse_bar_info
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(marketing_time) = #{statDate} AND buy_date IS NULL
        """)
    BigDecimal sumMarketingWeight(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /** 送宰总重(外购)：当日送宰的外购生猪总重 = Σ outsource.pig_weight（按送宰日 slaughter_date；与自产相加）。 */
    @Select("""
        SELECT COALESCE(SUM(pig_weight), 0) FROM t_warehouse_outsource_pig
        WHERE del_flag = '0' AND tenant_id = #{tenantId} AND DATE(slaughter_date) = #{statDate}
        """)
    BigDecimal sumOutsourceWeight(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 分割白条数：当日转入分割车间的白条数量，半只计 0.5、整只计 1（row195 客户最新口径）。
     *
     * <p>cut_record 一行 = 一次领用；{@code is_half}=1 半扇（0.5）/ =2 整只（1）。按领用行逐行加权求和
     * （整猪拆 2 半只分次领用 → 2 行各 0.5 = 1，与整只一致；只领 1 个半扇 → 0.5）。
     * white_bar_id 为 NULL 的行不计入（非整白条）。</p>
     */
    @Select("""
        SELECT COALESCE(SUM(CASE WHEN is_half = 1 THEN 0.5 WHEN is_half = 2 THEN 1 ELSE 1 END), 0)
        FROM t_warehouse_pig_cut_record
        WHERE del_flag = '0' AND tenant_id = #{tenantId} AND DATE(pickup_time) = #{statDate}
          AND white_bar_id IS NOT NULL
          AND out_type = 'cut'
        """)
    java.math.BigDecimal countCutBar(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /** 分割白条总重：当日白条出库总重 = Σ cut_record.pickup_weight（pickup_time）。仅计分割领用（out_type='cut'）。 */
    @Select("""
        SELECT COALESCE(SUM(pickup_weight), 0) FROM t_warehouse_pig_cut_record
        WHERE del_flag = '0' AND tenant_id = #{tenantId} AND DATE(pickup_time) = #{statDate}
          AND out_type = 'cut'
        """)
    BigDecimal sumCutBarWeight(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 分割产品总重：当日「对白条分割产出的猪肉产品」总重
     * = Σ stock_flow.change_quantity（flow_type='cut_out_in'，flow_date）。
     *
     * <p>口径（测试 row188）：「后管白条分割管理菜单对白条进行分割，产生的猪肉产品总重量」。
     * 分割产出（cut_out_in）是 {@code submitCutOut} 每部位入冻品库时写的不可变审计流水，与
     * {@code bar_info.cut_product_weight}（{@link org.dromara.djs.warehouse.flow.mapper.StockFlowMapper#sumCutOutByWhiteBarId}
     * 按 white_bar_id 聚合）同源，本方法按 flow_date 做日维度聚合。</p>
     *
     * <p><b>不读 product_inhouse</b>：product_inhouse 是燎毛入库产出行（整只/半只/猪头/猪蹄 raw 白条重，
     * 含 belong_type='pork' 的猪头/猪蹄副产），是分割的「原料」不是「产出」。按它聚合会把整白条燎毛重
     * （且含副产）当成分割产品，数值远大于领用重（物理不可能，如 07-03：燎毛口径 601 &gt; 领用 303.8），
     * 故改读 cut_out_in 分割产出流水（07-03 = 159，≤ 领用重，符合口径）。</p>
     */
    @Select("""
        SELECT COALESCE(SUM(change_quantity), 0) FROM t_warehouse_stock_flow
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(flow_date) = #{statDate} AND flow_type = 'cut_out_in'
        """)
    BigDecimal sumCutProductWeight(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /** 某损耗类型当日总重（防重复计：所有损耗一律从 loss_flow 按 loss_type 取，A#8）。 */
    @Select("""
        SELECT COALESCE(SUM(loss_weight), 0) FROM t_warehouse_loss_flow
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(loss_date) = #{statDate} AND loss_type = #{lossType}
        """)
    BigDecimal sumLossByType(@Param("tenantId") String tenantId, @Param("statDate") String statDate, @Param("lossType") String lossType);

    /**
     * 某损耗类型当日果蔬(belong_type='vegetable')总重（净菜段专用，row206）。
     * 与全口径 {@link #sumLossByType} 区分：净菜段的生产损耗/录入损耗只算果蔬产品，
     * 不把 pork 的 production_loss（如精瘦肉）计入果蔬指标。
     */
    @Select("""
        SELECT COALESCE(SUM(loss_weight), 0) FROM t_warehouse_loss_flow
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(loss_date) = #{statDate} AND loss_type = #{lossType}
          AND belong_type = 'vegetable'
        """)
    BigDecimal sumVegLossByType(@Param("tenantId") String tenantId, @Param("statDate") String statDate, @Param("lossType") String lossType);

    /**
     * 毛菜称量总重：当日毛菜处理间「处理完成」地块的毛菜总重 = Σ picked_weight
     * （口径 r103：只计当日处理完成的地块，按 is_finish=1 + 完成日 DATE(pick_end_time) 锚定，
     * 不再按采摘开始 pick_start_time）。
     */
    @Select("""
        SELECT COALESCE(SUM(picked_weight), 0) FROM t_warehouse_vegetable_handle
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND is_finish = 1 AND DATE(pick_end_time) = #{statDate}
        """)
    BigDecimal sumVegWeighWeight(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 发往月台果蔬总重：当日实际发往月台重 = Σ handle_record.record_weight（handle_target=2，
     * 按发往动作时间 handle_time 锚定，与作物维 {@link #selectCropHandleAgg} 的发往口径一致）。
     *
     * <p>旧写法读 vegetable_handle.send_platform_weight 按 pick_start_time：地块「早日采摘、当日才
     * 发往月台」时，发往重挂在采摘日、当日漏计（row103#3）。改从 handle_record 按 handle_time 归当日。</p>
     */
    @Select("""
        SELECT COALESCE(SUM(record_weight), 0) FROM t_warehouse_handle_record
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(handle_time) = #{statDate} AND handle_target = 2
        """)
    BigDecimal sumSendPlatformWeight(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /** 月台接收果蔬总重：当日 Σ veg_receive.weight（自产 receive_type=1，A#9，receive_time）。 */
    @Select("""
        SELECT COALESCE(SUM(weight), 0) FROM t_warehouse_veg_receive
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(receive_time) = #{statDate} AND receive_type = 1
        """)
    BigDecimal sumReceivePlatformWeight(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 净菜生产段三项库存流水量（row206 果蔬生产损耗残差用，均按果蔬 belong_type='vegetable' 口径）：
     * 领用 {@code prod_pick_out} / 退回 {@code prod_return_in} / 饲喂 {@code feed_out}（change_quantity, flow_date）。
     * <p>饲喂取 {@code stock_flow.feed_out}（从生产领用池出库、天然 ≤ 领用），<b>不是</b> feed_log 的
     * 毛菜间/仓库饲喂。{@code belong_type='vegetable'} = 自产果蔬（外购商品 belong_type 空、包材非 vegetable，
     * 天然排除，符合「只算产品的」）；stock_flow 无 belong_type 列，JOIN 商品主数据按 vegetable 过滤。
     * 与邓博 row38 {@code ProductionLossAggregateMapper} 同源同口径，仅少 GROUP BY（此处日维度整体聚合）。</p>
     *
     * @return 单行 {@code {pickOut, returnIn, feedOut}}
     */
    @Select("""
        SELECT COALESCE(SUM(CASE WHEN sf.flow_type = 'prod_pick_out'  THEN sf.change_quantity ELSE 0 END), 0) AS pickOut,
               COALESCE(SUM(CASE WHEN sf.flow_type = 'prod_return_in' THEN sf.change_quantity ELSE 0 END), 0) AS returnIn,
               COALESCE(SUM(CASE WHEN sf.flow_type = 'feed_out'       THEN sf.change_quantity ELSE 0 END), 0) AS feedOut
        FROM t_warehouse_stock_flow sf
        JOIN t_warehouse_product_info p ON p.id = sf.product_id
        WHERE sf.del_flag = '0' AND sf.tenant_id = #{tenantId}
          AND DATE(sf.flow_date) = #{statDate}
          AND p.belong_type = 'vegetable'
          AND sf.flow_type IN ('prod_pick_out', 'prod_return_in', 'feed_out')
        """)
    Map<String, Object> selectVegProdFlow(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 果蔬打包生产使用量：当日果蔬产品「打包生产」耗用的原料总重（row206，果蔬生产损耗残差减项）。
     * = Σ {@code t_warehouse_product_production.material_consume}（原材料耗用 kg；缺省回退 {@code product_weight}，
     * 二者对果蔬 1:1 相等且均为 kg），按 {@code produce_date} 归当日、JOIN 商品主数据按 {@code belong_type='vegetable'}
     * 过滤（= 自产果蔬产品；外购商品/包材天然排除，符合「只算产品的」）。
     */
    @Select("""
        SELECT COALESCE(SUM(COALESCE(pp.material_consume, pp.product_weight)), 0)
        FROM t_warehouse_product_production pp
        JOIN t_warehouse_product_info p ON p.id = pp.product_id
        WHERE pp.del_flag = '0' AND pp.tenant_id = #{tenantId}
          AND DATE(pp.produce_date) = #{statDate}
          AND p.belong_type = 'vegetable'
        """)
    BigDecimal sumVegProdPackUsage(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    // ============================================================
    //  作物日表（row17）源聚合 —— 按 crop_id 一次性 GROUP，service 端组装
    // ============================================================

    /**
     * 当日有任意处理记录的作物 ID 列表（不区分地块，只按作物，A#9）。
     * 取并集：毛菜处理（handle_record）/ 月台接收（veg_receive 自产）/ 饲喂台账（feed_log，仅有饲喂的作物也落盘）。
     *
     * <p>handle_record 支带 {@code record_weight > 0}：0 kg 收口记录（V6 r28/r29/r40/r41 允许提交）
     * 只表示「这块地称重/处理到此为止」，不代表当天真有产出。不过滤会让该作物在 t_warehouse_cropp_record
     * 落一整行全 0 的日报。同链路其它取数点（PICK_DETAIL_SQL / VEG_HANDLE_RECORD_SQL / 绩效 / 看板品种数）
     * 都已按此口径过滤，这里补齐。</p>
     */
    @Select("""
        <script>
        SELECT DISTINCT crop_id FROM (
          SELECT crop_id FROM t_warehouse_handle_record
          WHERE del_flag = '0' AND tenant_id = #{tenantId} AND DATE(handle_time) = #{statDate} AND crop_id IS NOT NULL
            AND record_weight &gt; 0
          UNION
          SELECT crop_id FROM t_warehouse_veg_receive
          WHERE del_flag = '0' AND tenant_id = #{tenantId} AND DATE(receive_time) = #{statDate}
            AND receive_type = 1 AND crop_id IS NOT NULL
          UNION
          SELECT crop_id FROM t_warehouse_feed_log
          WHERE del_flag = '0' AND tenant_id = #{tenantId} AND DATE(feed_date) = #{statDate} AND crop_id IS NOT NULL
        ) t
        </script>
        """)
    List<Long> selectActiveCropIds(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 作物维度毛菜处理聚合（按 crop_id GROUP，handle_record）：
     * 采摘量 = Σ record_type=1 record_weight；发往月台量 = Σ handle_target=2 record_weight。
     * 返 Map(cropId, pickWeight, sendWeight)。
     *
     * <p>饲喂量不在此查：饲喂走权威台账 t_warehouse_feed_log（含毛菜间 + 仓库领用两路径），
     * 由 {@link #selectCropFeedAgg} 单独聚合，避免与采摘/发往月台口径混。</p>
     */
    @Select("""
        SELECT crop_id AS cropId,
               COALESCE(SUM(CASE WHEN record_type = 1 THEN record_weight ELSE 0 END), 0) AS pickWeight,
               COALESCE(SUM(CASE WHEN handle_target = 2 THEN record_weight ELSE 0 END), 0) AS sendWeight
        FROM t_warehouse_handle_record
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(handle_time) = #{statDate} AND crop_id IS NOT NULL
        GROUP BY crop_id
        """)
    List<Map<String, Object>> selectCropHandleAgg(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 作物维度饲喂量（按 crop_id GROUP，权威台账 t_warehouse_feed_log）。
     * feed_log 含 crop_id × feed_date × feed_weight 直列（毛菜间 + 仓库领用饲喂两路径都落本表），
     * 故直接 SUM(feed_weight)，无需经 handle_record / plot 桥接。返 Map(cropId, feedWeight)。
     */
    @Select("""
        SELECT crop_id AS cropId, COALESCE(SUM(feed_weight), 0) AS feedWeight
        FROM t_warehouse_feed_log
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(feed_date) = #{statDate} AND crop_id IS NOT NULL
        GROUP BY crop_id
        """)
    List<Map<String, Object>> selectCropFeedAgg(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /** 作物维度月台接收量（按 crop_id GROUP，veg_receive 自产 receive_type=1）。返 Map(cropId, receiveWeight)。 */
    @Select("""
        SELECT crop_id AS cropId, COALESCE(SUM(weight), 0) AS receiveWeight
        FROM t_warehouse_veg_receive
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(receive_time) = #{statDate} AND receive_type = 1 AND crop_id IS NOT NULL
        GROUP BY crop_id
        """)
    List<Map<String, Object>> selectCropReceiveAgg(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 作物维度生产领用 / 退回（stock_flow 只有 plot_id 无 crop_id；经 t_warehouse_planting_record
     * 的 plot_id→crop_id 桥接到作物，crop_id 直接对 t_plant_crop_info.id）。返 Map(cropId, pickWeight, returnWeight)。
     * 一地块可能多条种植记录（轮作）→ 取 statDate 当日有效（plant_date<=statDate 且 harvest_date>=statDate，
     * 日期可空放宽）的最近一条，避免行膨胀重复计。
     */
    @Select("""
        SELECT pr.crop_id AS cropId,
               COALESCE(SUM(CASE WHEN sf.flow_type = 'prod_pick_out' THEN sf.change_quantity ELSE 0 END), 0) AS pickWeight,
               COALESCE(SUM(CASE WHEN sf.flow_type = 'prod_return_in' THEN sf.change_quantity ELSE 0 END), 0) AS returnWeight
        FROM t_warehouse_stock_flow sf
        JOIN t_warehouse_planting_record pr ON pr.plot_id = sf.plot_id AND pr.del_flag = '0' AND pr.tenant_id = #{tenantId}
          AND pr.id = (SELECT p2.id FROM t_warehouse_planting_record p2
                        WHERE p2.plot_id = sf.plot_id AND p2.del_flag = '0' AND p2.tenant_id = #{tenantId}
                          AND (p2.plant_date IS NULL OR p2.plant_date <= #{statDate})
                          AND (p2.harvest_date IS NULL OR p2.harvest_date >= #{statDate})
                        ORDER BY p2.plant_date DESC, p2.id DESC LIMIT 1)
        WHERE sf.del_flag = '0' AND sf.tenant_id = #{tenantId}
          AND DATE(sf.flow_date) = #{statDate}
          AND sf.flow_type IN ('prod_pick_out', 'prod_return_in')
          AND sf.plot_id IS NOT NULL
        GROUP BY pr.crop_id
        """)
    List<Map<String, Object>> selectCropFlowAgg(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 作物维度损耗（loss_flow 按损耗类型用不同归集键 → crop_id）。返 Map(cropId, vegHandleLoss, productionLoss, manualLoss)。
     *
     * <p><b>归集键按 loss_type 分两路</b>：</p>
     * <ul>
     *   <li>毛菜损耗 veg_handle_loss：毛菜间产生、带 plot_id → 经 t_warehouse_planting_record
     *       的 plot_id→crop_id 桥接（取 statDate 当日有效的最近一条种植记录，避免轮作多记录行膨胀）。</li>
     *   <li>生产损耗 production_loss / 录入损耗 manual_loss：product 维度产生、plot_id 多为 NULL，
     *       经 product_id → t_plant_crop_info.related_product 反解到 crop_id（crop.related_product = lf.product_id）。
     *       原 plot 桥接会因 plot_id NULL 整条漏量，改 product 归集补全。</li>
     * </ul>
     * <p>两路按 crop_id UNION ALL 后外层再 GROUP，service 端按 crop 取三损耗值。</p>
     */
    @Select("""
        SELECT cropId,
               COALESCE(SUM(vegHandleLoss), 0) AS vegHandleLoss,
               COALESCE(SUM(productionLoss), 0) AS productionLoss,
               COALESCE(SUM(manualLoss), 0) AS manualLoss
        FROM (
          SELECT pr.crop_id AS cropId,
                 lf.loss_weight AS vegHandleLoss,
                 0 AS productionLoss,
                 0 AS manualLoss
          FROM t_warehouse_loss_flow lf
          JOIN t_warehouse_planting_record pr ON pr.plot_id = lf.plot_id AND pr.del_flag = '0' AND pr.tenant_id = #{tenantId}
            AND pr.id = (SELECT p2.id FROM t_warehouse_planting_record p2
                          WHERE p2.plot_id = lf.plot_id AND p2.del_flag = '0' AND p2.tenant_id = #{tenantId}
                            AND (p2.plant_date IS NULL OR p2.plant_date <= #{statDate})
                            AND (p2.harvest_date IS NULL OR p2.harvest_date >= #{statDate})
                          ORDER BY p2.plant_date DESC, p2.id DESC LIMIT 1)
          WHERE lf.del_flag = '0' AND lf.tenant_id = #{tenantId}
            AND DATE(lf.loss_date) = #{statDate}
            AND lf.loss_type = 'veg_handle_loss'
            AND lf.plot_id IS NOT NULL
          UNION ALL
          SELECT c.id AS cropId,
                 0 AS vegHandleLoss,
                 CASE WHEN lf.loss_type = 'production_loss' THEN lf.loss_weight ELSE 0 END AS productionLoss,
                 CASE WHEN lf.loss_type = 'manual_loss'     THEN lf.loss_weight ELSE 0 END AS manualLoss
          FROM t_warehouse_loss_flow lf
          JOIN t_plant_crop_info c ON c.related_product = lf.product_id AND c.del_flag = '0' AND c.tenant_id = #{tenantId}
          WHERE lf.del_flag = '0' AND lf.tenant_id = #{tenantId}
            AND DATE(lf.loss_date) = #{statDate}
            AND lf.loss_type IN ('production_loss', 'manual_loss')
            AND lf.product_id IS NOT NULL
        ) u
        GROUP BY cropId
        """)
    List<Map<String, Object>> selectCropLossAgg(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    // ============================================================
    //  仓库月表（row18）源聚合 —— 从已落盘日表 Σ 回读
    // ============================================================

    /**
     * 汇总某月已落盘日表（屠宰头数之和 + 各分子/分母 Σ），月率用 Σ 分子÷Σ 分母（非日率平均）。
     * 返 Map(slaughterCount, sumRateArrive, sumRateBase, sumBarYieldNumer, sumBarYieldBase, sumCutProduct, sumCutBar)。
     *
     * <p>屠宰率 / 白条出品率的分子分母各有自己的 cohort 基数列（日表落盘时一并写下），月率必须拿这些
     * 基数 Σ 后再相除，不能拿 {@code arrive_weight} / {@code slaughter_weight} 凑——那两列是各自 cohort
     * 的全量，跟比率的口径不是同一批猪。</p>
     *
     * <p>白条出品率 = Σ{@code bar_yield_numer_weight} ÷ Σ{@code bar_yield_base_weight}，与日率同口径
     * （甲方 2026-09-07 口径：分子 = 当日处理完成的白条总重、分母 = 完成接收重量的猪只出栏重量之和，
     * 与屠宰率共用同一个分母）。必须走这两列而不是拿日比率求平均，也不能用
     * {@code finished_arrive_weight}（诊断列）。</p>
     *
     * @param month yyyy-MM
     */
    @Select("""
        SELECT COALESCE(SUM(slaughter_count), 0)              AS slaughterCount,
               COALESCE(SUM(slaughter_rate_arrive_weight), 0) AS sumRateArrive,
               COALESCE(SUM(slaughter_rate_base_weight), 0)   AS sumRateBase,
               COALESCE(SUM(bar_yield_numer_weight), 0)       AS sumBarYieldNumer,
               COALESCE(SUM(bar_yield_base_weight), 0)        AS sumBarYieldBase,
               COALESCE(SUM(cut_product_weight), 0)           AS sumCutProduct,
               COALESCE(SUM(cut_bar_weight), 0)               AS sumCutBar
        FROM t_warehouse_indicator_record
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE_FORMAT(stat_date, '%Y-%m') = #{month}
        """)
    Map<String, Object> sumMonthlyFromDaily(@Param("tenantId") String tenantId, @Param("month") String month);
}
