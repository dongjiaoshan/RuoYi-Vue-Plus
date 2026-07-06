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
 *   <li>{@code t_warehouse_pig_burn_record}：屠宰头数 / 接收重量 / 白条总重（burn_time）</li>
 *   <li>{@code t_warehouse_bar_info} + {@code t_warehouse_outsource_pig}：送宰总重（自产 marketing + 外购 pig_weight）</li>
 *   <li>{@code t_warehouse_pig_cut_record}：分割白条数 / 分割白条总重（pickup_time + pickup_weight）</li>
 *   <li>{@code t_warehouse_product_inhouse}：分割产品总重（white_bar_id 非空 = 猪肉，produce_date）</li>
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
     * 屠宰头数：当日燎毛间入库称重的猪只头数 = 当日在燎毛间称重的整白条（整猪）数。
     *
     * <p>源为 {@code t_warehouse_bar_info}（白条主表，一行 = 一头整猪/整白条），按「入库称重」时刻
     * {@code in_time}（weighBurn 回填）落当天 + {@code in_method=1}（燎毛间）+ {@code arrive_weight}
     * 非空（已称重）计。一头猪一行、天然去重——<b>不能</b>走 {@code t_warehouse_pig_burn_record}
     * 按 {@code COALESCE(ear_no, burn_id)} 去重：burn_id 是「产出行」粒度（同一头在燎毛间拆两个半只/
     * 猪头/猪蹄逐条录入 → 多条 burn 记录、各自 burn_id 不同），外购猪 ear_no 又为 NULL 退到 burn_id，
     * 一头整猪被拆成 N 条重复计（row198 客户实证「一头计 4」）。</p>
     */
    @Select("""
        SELECT COUNT(*) FROM t_warehouse_bar_info
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND in_method = 1 AND arrive_weight IS NOT NULL AND DATE(in_time) = #{statDate}
        """)
    int countSlaughter(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 接收重量：当日燎毛间入库称重总重 = Σ 每头整猪到场重 {@code arrive_weight}（row93）。
     *
     * <p>与 {@link #countSlaughter} 同源同口径：源 {@code t_warehouse_bar_info}（一行 = 一头整猪），
     * 按「入库称重」{@code in_time} 落当天 + {@code in_method=1} + {@code arrive_weight} 非空求和。
     * 一头一行、天然不重复。<b>不能</b>走 burn_record {@code GROUP BY COALESCE(ear_no, burn_id)}：
     * 外购猪 ear_no 空退 burn_id、同一头拆多条产出行 → arrive_weight 被按产出行重复累加偏大
     * （row199 客户实证）。</p>
     */
    @Select("""
        SELECT COALESCE(SUM(arrive_weight), 0) FROM t_warehouse_bar_info
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND in_method = 1 AND arrive_weight IS NOT NULL AND DATE(in_time) = #{statDate}
        """)
    BigDecimal sumArriveWeight(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /** 白条总重：当日白条库入库白条产品总重 = Σ burn.burn_weight。 */
    @Select("""
        SELECT COALESCE(SUM(burn_weight), 0) FROM t_warehouse_pig_burn_record
        WHERE del_flag = '0' AND tenant_id = #{tenantId} AND DATE(burn_time) = #{statDate}
        """)
    BigDecimal sumBarTotalWeight(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /** 送宰总重(自产)：当日出栏送宰猪总重 = Σ bar.marketing_weight（外购白条 buy_date 非空，排除）。 */
    @Select("""
        SELECT COALESCE(SUM(marketing_weight), 0) FROM t_warehouse_bar_info
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(marketing_time) = #{statDate} AND buy_date IS NULL
        """)
    BigDecimal sumMarketingWeight(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /** 送宰总重(外购)：当日外购猪总重 = Σ outsource.pig_weight（按 slaughter_date）。 */
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

    /** 果蔬生产领用总重：当日 stock_flow flow_type=prod_pick_out 变更量绝对值之和（change_quantity，flow_date）。 */
    @Select("""
        SELECT COALESCE(SUM(change_quantity), 0) FROM t_warehouse_stock_flow
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(flow_date) = #{statDate} AND flow_type = 'prod_pick_out'
        """)
    BigDecimal sumProdPickWeight(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /** 生产退回总重：当日 stock_flow flow_type=prod_return_in 变更量绝对值之和（净菜损耗率分母用）。 */
    @Select("""
        SELECT COALESCE(SUM(change_quantity), 0) FROM t_warehouse_stock_flow
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(flow_date) = #{statDate} AND flow_type = 'prod_return_in'
        """)
    BigDecimal sumProdReturnWeight(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 当日饲喂总重（全量 Σ feed_log.feed_weight，不按 crop 过滤，日表 row16 生产损耗残差用）。
     * 与作物维 {@link #selectCropFeedAgg}（按 crop_id GROUP + 要求 crop_id 非空）区分：本方法计
     * 当日全部饲喂路径（毛菜间 + 仓库领用）总重，含 crop_id 为 NULL 的行，供日表残差公式减去。
     */
    @Select("""
        SELECT COALESCE(SUM(feed_weight), 0) FROM t_warehouse_feed_log
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(feed_date) = #{statDate}
        """)
    BigDecimal sumFeedWeightTotal(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    // ============================================================
    //  作物日表（row17）源聚合 —— 按 crop_id 一次性 GROUP，service 端组装
    // ============================================================

    /**
     * 当日有任意处理记录的作物 ID 列表（不区分地块，只按作物，A#9）。
     * 取并集：毛菜处理（handle_record）/ 月台接收（veg_receive 自产）/ 饲喂台账（feed_log，仅有饲喂的作物也落盘）。
     */
    @Select("""
        <script>
        SELECT DISTINCT crop_id FROM (
          SELECT crop_id FROM t_warehouse_handle_record
          WHERE del_flag = '0' AND tenant_id = #{tenantId} AND DATE(handle_time) = #{statDate} AND crop_id IS NOT NULL
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
     * 返 Map(slaughterCount, sumArrive, sumSlaughterWeight, sumBarTotal, sumCutProduct, sumCutBar)。
     *
     * @param month yyyy-MM
     */
    @Select("""
        SELECT COALESCE(SUM(slaughter_count), 0)    AS slaughterCount,
               COALESCE(SUM(arrive_weight), 0)      AS sumArrive,
               COALESCE(SUM(slaughter_weight), 0)   AS sumSlaughterWeight,
               COALESCE(SUM(bar_total_weight), 0)   AS sumBarTotal,
               COALESCE(SUM(cut_product_weight), 0) AS sumCutProduct,
               COALESCE(SUM(cut_bar_weight), 0)     AS sumCutBar
        FROM t_warehouse_indicator_record
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE_FORMAT(stat_date, '%Y-%m') = #{month}
        """)
    Map<String, Object> sumMonthlyFromDaily(@Param("tenantId") String tenantId, @Param("month") String month);
}
