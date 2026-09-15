package org.dromara.djs.warehouse.vegout.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutBatchVo;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutCandidateRow;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutDetailVo;

import java.util.Date;
import java.util.List;

/**
 * 毛菜间出库查询 mapper（admin row187）。
 *
 * <p>没有独立主表：出库单 = {@code t_warehouse_stock_flow} 里同一个 {@code batch_no} 的若干行聚合，
 * 明细即那些行本身。故本 mapper 只有查询，没有 BaseMapper CRUD。</p>
 *
 * <p>显式 {@code tenant_id='1001' AND del_flag='0'}（V1 无全局租户拦截器）。</p>
 *
 * @author djs
 */
public interface VegOutMapper {

    /**
     * 新增抽屉左侧可选产品：白名单库位里的<b>原材料</b>库存行，一行一个库存篮。
     *
     * <p>只列 {@code product_stock > 0} 的行（库存为 0 没法出库）。按产品名模糊筛。</p>
     *
     * <p><b>两道范围约束缺一不可</b>：</p>
     * <ul>
     *   <li>{@code product_attr = 2}（字典 {@code djs_product_attr}，2=原材料）—— 这个窗口卖的是
     *       原材料，打包成品（生产产品）有各自的发货 / 门店链路，混进来会被当成散货按 kg 卖掉。</li>
     *   <li>库位白名单 —— 光按原材料筛远远不够：包材库 / 种子库 / 肥料库 / 生物农药库里的行同样是
     *       {@code product_attr=2}，只有库位能把「可售农产品」与「生产投入品」分开。</li>
     * </ul>
     *
     * <p>行分三个业态：果蔬有 {@code plot_id}（采摘来的地块篮）；猪肉靠 {@code ear_no} 追溯到具体猪只
     * （分割间建篮时写入，外购白条无耳号）；干货 / 蛋类 / 其他两者都没有。三期货无 plot_id，靠
     * {@code third_phase} 标识在「地块」列显示「三期」。</p>
     *
     * <p>{@code locationName} 是这个篮子<b>实际所在库位</b>的名称（前端「存储仓库」列）—— 工人照它去
     * 哪个库拿货，不是产品主数据上配置的 {@code store_location_id}（那只是建议落点，篮子可能不在那）。</p>
     *
     * <p>{@code productCode} 取 {@code t_warehouse_product_info.product_id}（业务码，非主键）：
     * 前端「已选产品」与打印单按它把同一产品的多个篮子合成一条（V6 row108）。</p>
     *
     * @param locationCodes 库位编码白名单
     * @param belongTypes   产品业态白名单
     * @param productName   产品名称（模糊，可空）
     * @return 可选产品行
     */
    @Select("""
        <script>
        SELECT s.id                AS stockId,
               s.product_id        AS productId,
               p.product_id        AS productCode,
               p.product_name      AS productName,
               p.product_spec      AS productSpec,
               s.product_stock     AS stockWeight,
               p.product_unit      AS productUnit,
               s.plot_id           AS plotId,
               pl.plot_code        AS plotCode,
               pl.plot_name        AS plotName,
               s.ear_no            AS earNo,
               l.location_name     AS locationName,
               s.third_phase       AS thirdPhase,
               p.belong_type       AS belongType,
               p.sale_price        AS salePrice
          FROM t_warehouse_location_stock s
          JOIN t_warehouse_location_info l ON l.id = s.location_id AND l.del_flag = '0'
          JOIN t_warehouse_product_info p  ON p.id = s.product_id  AND p.del_flag = '0'
          LEFT JOIN t_plant_plot_info pl   ON pl.id = s.plot_id    AND pl.del_flag = '0'
         WHERE s.del_flag = '0'
           AND s.tenant_id = '1001'
           AND l.location_code IN
           <foreach collection="locationCodes" item="lc" open="(" separator="," close=")">#{lc}</foreach>
           AND p.belong_type IN
           <foreach collection="belongTypes" item="bt" open="(" separator="," close=")">#{bt}</foreach>
           <!-- 字典 djs_product_attr：2=原材料。生产产品走发货 / 门店链路，不从这个窗口散卖。 -->
           AND p.product_attr = 2
           AND s.product_stock &gt; 0
        <if test="productName != null and productName != ''">
           AND p.product_name LIKE CONCAT('%', #{productName}, '%')
        </if>
         <!-- 先进先出：合并成一行后服务端按这个顺序跨篮扣减（D-0068「出库先进先出自动」）。
              create_time 同刻（同一次批量建篮）时按 id 兜底，保证顺序确定、不随查询计划漂移。 -->
         ORDER BY p.product_name, pl.plot_code, s.create_time, s.id
        </script>
        """)
    List<VegOutCandidateRow> selectCandidates(@Param("locationCodes") java.util.Collection<String> locationCodes,
                                             @Param("belongTypes") java.util.Collection<String> belongTypes,
                                             @Param("productName") String productName);

    /**
     * 出库单聚合 SQL（按 {@code batch_no} 分组），分页列表与导出全量共用一份。
     *
     * <p><b>kg 的判据必须与全仓对齐</b>：`kg`（大小写 / 前后空格不敏感）、`公斤`、以及单位缺失（按 kg 兜底）。
     * 产品单位是自由文本输入框、没有字典约束，工人把它敲成「公斤」是会发生的；漏认公斤，
     * 那个产品的重量就会落进「出库量」计件列 —— 复现的正是甲方 row220 抱怨的同一类现象。
     * 同判据另见 {@code ProductProductionMapper}（`IN ('kg','公斤')`，甲方明确要求）与前端
     * {@code utils/weight.ts#isKgUnit}。</p>
     *
     * <p><b>totalWeight 与 totalQty 互补二分</b>（V6 row220）：kg 行（含单位缺失，按 kg 兜底）进重量列，
     * 其余单位（袋 / 桶 / 罐 / 枚 / 份）进出库量列。同一个 {@code LOWER(TRIM(COALESCE(unit,'kg'))) IN ('kg','')}
     * 判据取反复用，两列不会重叠也不会漏行 —— 拆成两套写法迟早会分叉，那时页面上「重量 + 出库量」
     * 加起来就对不上这张单实际出了多少行。</p>
     *
     * <p><b>totalWeight 只累加 kg 行</b>（row194 D7 口径）：候选自 row194 起扩到干货库 / 蛋类库，
     * 单位混杂 kg / 袋 / 桶 / 罐 / 枚，把「3 袋」「100 枚」直接加进 kg 合计会得到一个没有物理意义的数
     * （实测「10kg + 3袋 + 2kg + 100枚」曾被算成 115.000kg，而新增抽屉同一单算的是 12.000kg —— 两处打架）。
     * <b>金额则全部累加</b>，与抽屉汇总行「共 N 个品类 · X kg · ¥Y」同一口径。</p>
     *
     * <p>抽成常量而不是让导出方法再抄一份：两处口径一旦分叉，导出的合计就会和页面显示的对不上，
     * 而这正是甲方拿导出去对账的那两列。文本块是编译期常量，可直接用于 {@code @Select}。</p>
     */
    String BATCH_AGG_SQL = """
        <script>
        SELECT f.batch_no                    AS batchNo,
               MIN(f.flow_date)              AS outDate,
               MIN(f.stock_out_dest)         AS outDest,
               COUNT(DISTINCT f.product_id)  AS productKinds,
               SUM(CASE WHEN LOWER(TRIM(COALESCE(p.product_unit, 'kg'))) IN ('kg', '公斤', '')
                        THEN f.change_quantity ELSE 0 END)             AS totalWeight,
               SUM(CASE WHEN LOWER(TRIM(COALESCE(p.product_unit, 'kg'))) IN ('kg', '公斤', '')
                        THEN 0 ELSE f.change_quantity END)             AS totalQty,
               SUM(f.change_quantity * COALESCE(f.out_unit_price, 0))  AS totalAmount,
               MIN(f.operator_id)            AS operatorId
          FROM t_warehouse_stock_flow f
          LEFT JOIN t_warehouse_product_info p ON p.id = f.product_id AND p.del_flag = '0'
         WHERE f.del_flag = '0'
           AND f.tenant_id = '1001'
           AND f.batch_no IS NOT NULL
           <!-- batch_no 是 stock_flow 的通用列，今天只有毛菜间出库在用；不加这道守卫，
                将来任何写 batch_no 的功能都会漏进这张出库单列表。与 V202608280100 的重编号口径保持一致。 -->
           AND f.flow_type = 'backstage_out'
        <if test="beginDate != null">
           AND f.flow_date &gt;= #{beginDate}
        </if>
        <if test="endDate != null">
           AND f.flow_date &lt;= #{endDate}
        </if>
        <if test="outDest != null and outDest != ''">
           AND f.stock_out_dest = #{outDest}
        </if>
        <if test="operatorId != null">
           AND f.operator_id = #{operatorId}
        </if>
         GROUP BY f.batch_no
         ORDER BY MIN(f.flow_date) DESC, f.batch_no DESC
        </script>
        """;

    /**
     * 出库单分页列表（row187 页面）。
     *
     * @return 分页出库单
     */
    @Select(BATCH_AGG_SQL)
    IPage<VegOutBatchVo> selectBatchPage(IPage<VegOutBatchVo> page,
                                         @Param("beginDate") Date beginDate,
                                         @Param("endDate") Date endDate,
                                         @Param("outDest") String outDest,
                                         @Param("operatorId") Long operatorId);

    /**
     * 出库单全量列表（V6 row31 导出）：同 {@link #selectBatchPage} 的筛选与口径，只是不分页。
     *
     * <p>不复用分页方法传一个「够大的 pageSize」—— 那样一旦数据量超过猜的那个数就会静默少导，
     * 而导出是拿去对账的，少一行看不出来。</p>
     *
     * @return 当前筛选条件下的全部出库单
     */
    @Select(BATCH_AGG_SQL)
    List<VegOutBatchVo> selectBatchList(@Param("beginDate") Date beginDate,
                                        @Param("endDate") Date endDate,
                                        @Param("outDest") String outDest,
                                        @Param("operatorId") Long operatorId);

    /**
     * 出库单明细：该 {@code batch_no} 下的产品行，可按产品名模糊筛。
     *
     * <p><b>按 产品 + 库位 + 耳号 + 地块 聚合成一行</b>（V6 row225 / D-0068「不分日期、不认批次」）：
     * 同一张单里同一产品拆成的多条流水（多个库存篮各一条）合并、出库量相加；
     * 耳号或地块不同的仍各自成行。{@code productCode} 供详情页「重新打印」再按产品编号合并
     * （V6 row108，与新增时打的那张单同一口径）—— 两层合并方向一致，不冲突。</p>
     *
     * <p>{@code GROUP BY} 里同时列了 {@code p.product_id} 与 {@code f.product_id}：前者是产品业务码
     * （展示 + 打印合并键），后者是流水指向的产品主键（真正的聚合维度）。只 group 业务码的话，
     * 万一两条产品主数据共用同一个业务码就会被错误合并。</p>
     *
     * <p>{@code warehouse_id} 是甲方点名的「产品库位」维度：同一产品在冻品库与猪肉鲜品库各有库存、
     * 在同一张单里各自选量<b>各自填单价</b>是真实可达的（新增抽屉逐行提交 {@code outUnitPrice}），
     * 漏掉它会把两条不同库位、不同单价的流水并成一行。{@code third_phase} 同理 ——
     * 三期货没有真实 {@code plot_id}，不入键会与普通货并成一行、「地块」列显示成哪个都不对。</p>
     *
     * <p>{@code earNo} / 地块是这条流水的来源标识（row191 弹框「规格」与「出库量」之间那两列）：
     * 猪肉行有耳号、果蔬行有地块，各自另一项为空，前端与导出都兜 {@code -}。
     * 两者取<b>流水自己</b>的 {@code ear_no} / {@code plot_id}，不回溯上游批次 ——
     * 出库时记的是哪个篮子，明细就显示哪个篮子。</p>
     *
     * <p>地块取 {@code plot_name} 而非 {@code plot_code}（row199 甲方口径「与出库时显示的保持一致」，
     * 新增抽屉那列显示的就是地块名）；再带 {@code third_phase}，让三期货
     * （没有真实 {@code plot_id}）也能在「地块」列显示「三期」，与库存查询 / 入出库记录三页同一口径。</p>
     */
    @Select("""
        <script>
        SELECT p.product_id     AS productCode,
               p.product_name   AS productName,
               p.product_spec   AS productSpec,
               p.product_unit   AS productUnit,
               SUM(f.change_quantity) AS outWeight,
               <!-- 合并组内单价不一致（含「一条有价一条没价」）时不给单一单价，前端显示「-」：
                    随便挑一条会让「单价 × 出库量 ≠ 出库总价」，看单据的人只能怀疑是算错了。
                    COALESCE 成 -1 参与去重，才能把 NULL 与真实价的混合也判成不一致。 -->
               CASE WHEN COUNT(DISTINCT COALESCE(f.out_unit_price, -1)) = 1
                    THEN MIN(f.out_unit_price) END                     AS outUnitPrice,
               SUM(f.change_quantity * COALESCE(f.out_unit_price, 0))  AS outAmount,
               f.ear_no         AS earNo,
               f.third_phase    AS thirdPhase,
               MIN(pl.plot_name)  AS plotName
          FROM t_warehouse_stock_flow f
          JOIN t_warehouse_product_info p ON p.id = f.product_id AND p.del_flag = '0'
          LEFT JOIN t_plant_plot_info pl  ON pl.id = f.plot_id   AND pl.del_flag = '0'
         WHERE f.del_flag = '0'
           AND f.tenant_id = '1001'
           AND f.batch_no = #{batchNo}
        <if test="productName != null and productName != ''">
           AND p.product_name LIKE CONCAT('%', #{productName}, '%')
        </if>
         GROUP BY p.product_id, p.product_name, p.product_spec, p.product_unit,
                  f.product_id, f.warehouse_id, f.ear_no, f.plot_id, f.third_phase
         ORDER BY p.product_name, f.ear_no, MIN(pl.plot_name)
        </script>
        """)
    List<VegOutDetailVo> selectBatchDetail(@Param("batchNo") String batchNo,
                                           @Param("productName") String productName);
}
