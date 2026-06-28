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
 *   <li>{@code t_warehouse_handle_record}：作物维度采摘 / 饲喂 / 发往月台（按 crop_id GROUP，handle_time）</li>
 *   <li>{@code t_warehouse_veg_receive}：月台接收（receive_type=1 自产，receive_time）</li>
 *   <li>{@code t_warehouse_stock_flow}：生产领用 / 生产退回（prod_pick_out / prod_return_in，flow_date）</li>
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

    /** 屠宰头数：当日燎毛间接收的猪只头数 = 当日 burn 记录数。 */
    @Select("""
        SELECT COUNT(*) FROM t_warehouse_pig_burn_record
        WHERE del_flag = '0' AND tenant_id = #{tenantId} AND DATE(burn_time) = #{statDate}
        """)
    int countSlaughter(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /** 接收重量：当日燎毛间称重总重 = Σ burn.arrive_weight。 */
    @Select("""
        SELECT COALESCE(SUM(arrive_weight), 0) FROM t_warehouse_pig_burn_record
        WHERE del_flag = '0' AND tenant_id = #{tenantId} AND DATE(burn_time) = #{statDate}
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

    /** 分割白条数：当日转入分割车间的白条数量 = 当日领用 cut_record 数（pickup_time）。 */
    @Select("""
        SELECT COUNT(*) FROM t_warehouse_pig_cut_record
        WHERE del_flag = '0' AND tenant_id = #{tenantId} AND DATE(pickup_time) = #{statDate}
        """)
    int countCutBar(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /** 分割白条总重：当日白条出库总重 = Σ cut_record.pickup_weight（pickup_time）。 */
    @Select("""
        SELECT COALESCE(SUM(pickup_weight), 0) FROM t_warehouse_pig_cut_record
        WHERE del_flag = '0' AND tenant_id = #{tenantId} AND DATE(pickup_time) = #{statDate}
        """)
    BigDecimal sumCutBarWeight(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /** 分割产品总重：当日分割车间产出产品重之和 = Σ product_inhouse.product_weight（white_bar_id 非空 = 猪肉，produce_date）。 */
    @Select("""
        SELECT COALESCE(SUM(product_weight), 0) FROM t_warehouse_product_inhouse
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(produce_date) = #{statDate} AND white_bar_id IS NOT NULL
        """)
    BigDecimal sumCutProductWeight(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /** 某损耗类型当日总重（防重复计：所有损耗一律从 loss_flow 按 loss_type 取，A#8）。 */
    @Select("""
        SELECT COALESCE(SUM(loss_weight), 0) FROM t_warehouse_loss_flow
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(loss_date) = #{statDate} AND loss_type = #{lossType}
        """)
    BigDecimal sumLossByType(@Param("tenantId") String tenantId, @Param("statDate") String statDate, @Param("lossType") String lossType);

    /** 毛菜称量总重：当日采摘称重之和 = Σ vegetable_handle.picked_weight（pick_start_time）。 */
    @Select("""
        SELECT COALESCE(SUM(picked_weight), 0) FROM t_warehouse_vegetable_handle
        WHERE del_flag = '0' AND tenant_id = #{tenantId} AND DATE(pick_start_time) = #{statDate}
        """)
    BigDecimal sumVegWeighWeight(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /** 发往月台果蔬总重：当日 Σ vegetable_handle.send_platform_weight（pick_start_time）。 */
    @Select("""
        SELECT COALESCE(SUM(send_platform_weight), 0) FROM t_warehouse_vegetable_handle
        WHERE del_flag = '0' AND tenant_id = #{tenantId} AND DATE(pick_start_time) = #{statDate}
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

    // ============================================================
    //  作物日表（row17）源聚合 —— 按 crop_id 一次性 GROUP，service 端组装
    // ============================================================

    /**
     * 当日有任意处理记录的作物 ID 列表（不区分地块，只按作物，A#9）。
     * 取并集：毛菜处理（handle_record）/ 月台接收（veg_receive 自产）。
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
        ) t
        </script>
        """)
    List<Long> selectActiveCropIds(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 作物维度毛菜处理聚合（按 crop_id GROUP，handle_record）：
     * 采摘量 = Σ record_type=1 record_weight；饲喂量 = Σ handle_target=3 record_weight；
     * 发往月台量 = Σ handle_target=2 record_weight。返 Map(cropId, pickWeight, feedWeight, sendWeight)。
     */
    @Select("""
        SELECT crop_id AS cropId,
               COALESCE(SUM(CASE WHEN record_type = 1 THEN record_weight ELSE 0 END), 0) AS pickWeight,
               COALESCE(SUM(CASE WHEN handle_target = 3 THEN record_weight ELSE 0 END), 0) AS feedWeight,
               COALESCE(SUM(CASE WHEN handle_target = 2 THEN record_weight ELSE 0 END), 0) AS sendWeight
        FROM t_warehouse_handle_record
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(handle_time) = #{statDate} AND crop_id IS NOT NULL
        GROUP BY crop_id
        """)
    List<Map<String, Object>> selectCropHandleAgg(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

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
     * 作物维度损耗（loss_flow 只有 plot_id 无 crop_id；经 t_warehouse_planting_record 的 plot_id→crop_id 桥接）。
     * 毛菜损耗 / 生产损耗 / 录入损耗按 loss_type 分桶。返 Map(cropId, vegHandleLoss, productionLoss, manualLoss)。
     * 桥接取 statDate 当日有效的最近一条种植记录，避免轮作多记录行膨胀。
     */
    @Select("""
        SELECT pr.crop_id AS cropId,
               COALESCE(SUM(CASE WHEN lf.loss_type = 'veg_handle_loss' THEN lf.loss_weight ELSE 0 END), 0) AS vegHandleLoss,
               COALESCE(SUM(CASE WHEN lf.loss_type = 'production_loss' THEN lf.loss_weight ELSE 0 END), 0) AS productionLoss,
               COALESCE(SUM(CASE WHEN lf.loss_type = 'manual_loss' THEN lf.loss_weight ELSE 0 END), 0) AS manualLoss
        FROM t_warehouse_loss_flow lf
        JOIN t_warehouse_planting_record pr ON pr.plot_id = lf.plot_id AND pr.del_flag = '0' AND pr.tenant_id = #{tenantId}
          AND pr.id = (SELECT p2.id FROM t_warehouse_planting_record p2
                        WHERE p2.plot_id = lf.plot_id AND p2.del_flag = '0' AND p2.tenant_id = #{tenantId}
                          AND (p2.plant_date IS NULL OR p2.plant_date <= #{statDate})
                          AND (p2.harvest_date IS NULL OR p2.harvest_date >= #{statDate})
                        ORDER BY p2.plant_date DESC, p2.id DESC LIMIT 1)
        WHERE lf.del_flag = '0' AND lf.tenant_id = #{tenantId}
          AND DATE(lf.loss_date) = #{statDate}
          AND lf.loss_type IN ('veg_handle_loss', 'production_loss', 'manual_loss')
          AND lf.plot_id IS NOT NULL
        GROUP BY pr.crop_id
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
