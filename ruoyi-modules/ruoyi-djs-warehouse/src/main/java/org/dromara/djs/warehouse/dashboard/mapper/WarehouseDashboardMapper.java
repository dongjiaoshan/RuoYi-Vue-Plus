package org.dromara.djs.warehouse.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.dashboard.domain.vo.ChartSeriesItemVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.ChartTrendPointVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.LocationOverviewItemVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.ProductionMonthRowVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 仓库看板聚合查询 Mapper。
 *
 * <p>本 mapper 不绑定单表实体；只提供 dashboard service 调用的原始聚合 SQL。
 * 数据源：{@code t_warehouse_demand_manage} + {@code t_warehouse_return_product}
 * + {@code t_warehouse_product_production} + {@code t_warehouse_stock_flow}
 * + {@code t_warehouse_check_record} + {@code t_warehouse_location_info} +
 * {@code t_warehouse_location_stock}。</p>
 *
 * <p><b>关键列约定（按实库结构）</b>：</p>
 * <ul>
 *   <li>需求表业态字段 {@code product_type} VARCHAR(32)，值 white_bar/vegetable/gift_box/other
 *       （字典 djs_demand_product_type）；需求量列 {@code demand_quantity}。</li>
 *   <li>退货表 {@code t_warehouse_return_product}：方向列 {@code return_direction}
 *       （store_to_warehouse / warehouse_to_supplier）；重量列 {@code return_weight}。</li>
 *   <li>生产表 {@code t_warehouse_product_production}：日期列 {@code produce_date} DATE，重量列
 *       {@code product_weight}（WMS-PACK-001 重建后；非旧稿 produce_quantity）。</li>
 *   <li>流水表 {@code t_warehouse_stock_flow}：方向列 {@code inout_type} CHAR(3) 取值
 *       IN=入库 / OT=出库（djs_inout_type）；业务类型 {@code flow_type} 含 loss=损耗；
 *       数量绝对值列 {@code change_quantity}；业务日期 {@code flow_date}。</li>
 *   <li>盘点表 {@code t_warehouse_check_record}：结果列 {@code check_result_type}
 *       （1 正常/2 异常/3 计损）；差异列 {@code diff_stock}；表头行 {@code is_header=1}（产品 ID 写 0、聚合需排除）；
 *       明细行 {@code is_header=0}；库位列 {@code location_id}。</li>
 *   <li>库位主表 {@code t_warehouse_location_info}；库存表 {@code t_warehouse_location_stock}
 *       库存列 {@code product_stock}。</li>
 * </ul>
 *
 * <p>dashboard 聚合不走 BaseMapperPlus，故所有 SQL 显式 {@code WHERE tenant_id = #{tenantId}}
 * （参照 BRD-DASH-001 AggregateQueryMapper 范式）。</p>
 *
 * @author djs
 */
@Mapper
public interface WarehouseDashboardMapper {

    // ============================== KPI 横条（summary 兼容 + charts 复用） ==============================

    /**
     * 今日某业态需求量合计（SUM demand_quantity）。
     *
     * @param tenantId    租户
     * @param productType 业态值（white_bar / vegetable / gift_box / other）
     * @return SUM(demand_quantity)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(demand_quantity), 0) "
        + "  FROM t_warehouse_demand_manage "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND product_type = #{productType} "
        + "   AND demand_date = CURDATE() "
        + "   AND del_flag = '0'")
    BigDecimal sumTodayDemandByType(@Param("tenantId") String tenantId, @Param("productType") String productType);

    /**
     * 今日白条需求量合计（summary KPI 兼容）。
     *
     * @param tenantId 租户
     * @return SUM(demand_quantity) WHERE product_type='white_bar'，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(demand_quantity), 0) "
        + "  FROM t_warehouse_demand_manage "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND product_type = 'white_bar' "
        + "   AND demand_date = CURDATE() "
        + "   AND del_flag = '0'")
    BigDecimal sumTodayWhiteBarDemand(@Param("tenantId") String tenantId);

    /**
     * 今日需求总量（全业态 SUM demand_quantity）。
     *
     * @param tenantId 租户
     * @return SUM(demand_quantity)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(demand_quantity), 0) "
        + "  FROM t_warehouse_demand_manage "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND demand_date = CURDATE() "
        + "   AND del_flag = '0'")
    BigDecimal sumTodayDemandTotal(@Param("tenantId") String tenantId);

    /**
     * 今日需求单数（去重 demand_no）。
     *
     * @param tenantId 租户
     * @return COUNT(DISTINCT demand_no)，无记录返 0
     */
    @Select("SELECT COUNT(DISTINCT demand_no) "
        + "  FROM t_warehouse_demand_manage "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND demand_date = CURDATE() "
        + "   AND del_flag = '0'")
    Integer countTodayDemandOrders(@Param("tenantId") String tenantId);

    /**
     * 今日生产笔数（COUNT product_production）。
     *
     * @param tenantId 租户
     * @return COUNT(*)，无记录返 0
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_product_production "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND produce_date = CURDATE() "
        + "   AND del_flag = '0'")
    Integer countTodayProduction(@Param("tenantId") String tenantId);

    /**
     * 今日生产重量（SUM product_weight）。
     *
     * @param tenantId 租户
     * @return SUM(product_weight)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(product_weight), 0) "
        + "  FROM t_warehouse_product_production "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND produce_date = CURDATE() "
        + "   AND del_flag = '0'")
    BigDecimal sumTodayProductionWeight(@Param("tenantId") String tenantId);

    /**
     * 今日某方向出入库笔数（inout_type IN / OT）。
     *
     * @param tenantId  租户
     * @param inoutType IN=入库 / OT=出库
     * @return COUNT(*)，无记录返 0
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND inout_type = #{inoutType} "
        + "   AND DATE(flow_date) = CURDATE() "
        + "   AND del_flag = '0'")
    Integer countTodayFlowByDirection(@Param("tenantId") String tenantId, @Param("inoutType") String inoutType);

    /**
     * 今日损耗量（flow_type='loss' SUM change_quantity 绝对值）。
     *
     * @param tenantId 租户
     * @return SUM(change_quantity)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(change_quantity), 0) "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND flow_type = 'loss' "
        + "   AND DATE(flow_date) = CURDATE() "
        + "   AND del_flag = '0'")
    BigDecimal sumTodayLoss(@Param("tenantId") String tenantId);

    // ============================== 盘点 KPI（summary 兼容） ==============================

    /**
     * 最近一天盘点正常明细数（check_result_type=1，仅明细行 is_header=0）。
     *
     * @param tenantId 租户
     * @return 正常明细条数
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_check_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND is_header = 0 "
        + "   AND check_result_type = 1 "
        + "   AND DATE(check_date) = (SELECT DATE(MAX(check_date)) FROM t_warehouse_check_record "
        + "                            WHERE tenant_id = #{tenantId} AND del_flag = '0' AND is_header = 0)")
    Integer countLatestCheckNormal(@Param("tenantId") String tenantId);

    /**
     * 最近一天盘点异常明细数（check_result_type=2，仅明细行）。
     *
     * @param tenantId 租户
     * @return 异常明细条数
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_check_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND is_header = 0 "
        + "   AND check_result_type = 2 "
        + "   AND DATE(check_date) = (SELECT DATE(MAX(check_date)) FROM t_warehouse_check_record "
        + "                            WHERE tenant_id = #{tenantId} AND del_flag = '0' AND is_header = 0)")
    Integer countLatestCheckAbnormal(@Param("tenantId") String tenantId);

    /**
     * 最近一天盘点计损明细数（check_result_type=3，仅明细行）。
     *
     * @param tenantId 租户
     * @return 计损明细条数
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_check_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND is_header = 0 "
        + "   AND check_result_type = 3 "
        + "   AND DATE(check_date) = (SELECT DATE(MAX(check_date)) FROM t_warehouse_check_record "
        + "                            WHERE tenant_id = #{tenantId} AND del_flag = '0' AND is_header = 0)")
    Integer countLatestCheckLoss(@Param("tenantId") String tenantId);

    /**
     * 当月异常库位数（COUNT DISTINCT location_id WHERE diff_stock!=0 AND 明细行 AND 当月）。
     *
     * @param tenantId 租户
     * @return 当月有盘点差异的不重复库位数
     */
    @Select("SELECT COUNT(DISTINCT location_id) "
        + "  FROM t_warehouse_check_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND is_header = 0 "
        + "   AND diff_stock <> 0 "
        + "   AND location_id IS NOT NULL "
        + "   AND check_date >= DATE_FORMAT(NOW(), '%Y-%m-01')")
    Integer countMonthAbnormalLocation(@Param("tenantId") String tenantId);

    /**
     * 当月正常库位数（盘点过、明细行、当月、无差异的不重复库位数）。
     *
     * @param tenantId 租户
     * @return 当月盘点无差异的不重复库位数
     */
    @Select("SELECT COUNT(DISTINCT location_id) "
        + "  FROM t_warehouse_check_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND is_header = 0 "
        + "   AND diff_stock = 0 "
        + "   AND location_id IS NOT NULL "
        + "   AND location_id NOT IN ( "
        + "         SELECT location_id FROM t_warehouse_check_record "
        + "          WHERE tenant_id = #{tenantId} AND del_flag = '0' AND is_header = 0 "
        + "            AND diff_stock <> 0 AND location_id IS NOT NULL "
        + "            AND check_date >= DATE_FORMAT(NOW(), '%Y-%m-01') ) "
        + "   AND check_date >= DATE_FORMAT(NOW(), '%Y-%m-01')")
    Integer countMonthNormalLocation(@Param("tenantId") String tenantId);

    // ============================== 6 图聚合 ==============================

    /**
     * 图①：近 30 日需求量按业态聚合（GROUP BY product_type）。
     *
     * <p>返回原始 product_type 值（white_bar 等）作为 name，由 service 映射中文标签并补齐 4 业态。</p>
     *
     * @param tenantId 租户
     * @return 各业态 name(=product_type) + value(=SUM demand_quantity)
     */
    @Select("SELECT product_type AS name, COALESCE(SUM(demand_quantity), 0) AS value "
        + "  FROM t_warehouse_demand_manage "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND demand_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) "
        + " GROUP BY product_type")
    List<ChartSeriesItemVo> selectDemandByType(@Param("tenantId") String tenantId);

    /**
     * 图②：近 30 日退货按方向构成（GROUP BY return_direction，COUNT）。
     *
     * @param tenantId 租户
     * @return 各方向 name(=return_direction) + value(=COUNT)
     */
    @Select("SELECT COALESCE(return_direction, 'store_to_warehouse') AS name, COUNT(*) AS value "
        + "  FROM t_warehouse_return_product "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) "
        + " GROUP BY return_direction")
    List<ChartSeriesItemVo> selectReturnByDirection(@Param("tenantId") String tenantId);

    /**
     * 图③：近 N 日生产趋势（按 produce_date 日聚合 SUM product_weight）。
     *
     * <p>仅返回有记录的日期，service 层按近 N 日补齐缺口日为 0。</p>
     *
     * @param tenantId 租户
     * @param days     近几日
     * @return 各日 date(yyyy-MM-dd) + value(=SUM product_weight)
     */
    @Select("SELECT DATE_FORMAT(produce_date, '%Y-%m-%d') AS date, COALESCE(SUM(product_weight), 0) AS value "
        + "  FROM t_warehouse_product_production "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND produce_date >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
        + " GROUP BY DATE_FORMAT(produce_date, '%Y-%m-%d') "
        + " ORDER BY date")
    List<ChartTrendPointVo> selectProductionTrend(@Param("tenantId") String tenantId, @Param("days") int days);

    /**
     * 图④：最近一个盘点单结果分布（GROUP BY check_result_type，仅明细行 is_header=0）。
     *
     * <p>取该租户最近盘点日（DATE(MAX(check_date))）当天明细，按 1 正常/2 异常/3 计损 计数。
     * 返回原始 check_result_type 数值字符串作为 name，由 service 映射中文。</p>
     *
     * @param tenantId 租户
     * @return name(=check_result_type) + value(=COUNT)
     */
    @Select("SELECT CAST(check_result_type AS CHAR) AS name, COUNT(*) AS value "
        + "  FROM t_warehouse_check_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND is_header = 0 "
        + "   AND DATE(check_date) = (SELECT DATE(MAX(check_date)) FROM t_warehouse_check_record "
        + "                            WHERE tenant_id = #{tenantId} AND del_flag = '0' AND is_header = 0) "
        + " GROUP BY check_result_type")
    List<ChartSeriesItemVo> selectCheckResult(@Param("tenantId") String tenantId);

    /**
     * 图⑥：近 N 日损耗趋势（stock_flow flow_type='loss' 按 flow_date 日聚合 SUM change_quantity）。
     *
     * <p>仅返回有记录的日期，service 层按近 N 日补齐缺口日为 0。</p>
     *
     * @param tenantId 租户
     * @param days     近几日
     * @return 各日 date(yyyy-MM-dd) + value(=SUM change_quantity)
     */
    @Select("SELECT DATE_FORMAT(flow_date, '%Y-%m-%d') AS date, COALESCE(SUM(change_quantity), 0) AS value "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND flow_type = 'loss' "
        + "   AND flow_date >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
        + " GROUP BY DATE_FORMAT(flow_date, '%Y-%m-%d') "
        + " ORDER BY date")
    List<ChartTrendPointVo> selectLossTrend(@Param("tenantId") String tenantId, @Param("days") int days);

    // ============================== 生产管理统计（mp 原型图 26 · 猪肉 tab） ==============================

    /**
     * 当年送宰头数（COUNT 燎毛记录，按 burn_time 当年）。
     *
     * @param tenantId 租户
     * @return COUNT(*)，无记录返 0
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_pig_burn_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND YEAR(burn_time) = YEAR(CURDATE())")
    Integer countYearBurn(@Param("tenantId") String tenantId);

    /**
     * 当年送宰均重 kg（AVG arrive_weight，燎毛记录当年）。
     *
     * @param tenantId 租户
     * @return AVG(arrive_weight)，无记录返 0
     */
    @Select("SELECT COALESCE(AVG(arrive_weight), 0) "
        + "  FROM t_warehouse_pig_burn_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND YEAR(burn_time) = YEAR(CURDATE())")
    BigDecimal avgYearBurnArriveWeight(@Param("tenantId") String tenantId);

    /**
     * 当年到场总重 kg（SUM arrive_weight，燎毛记录当年；屠宰出品率分母）。
     *
     * @param tenantId 租户
     * @return SUM(arrive_weight)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(arrive_weight), 0) "
        + "  FROM t_warehouse_pig_burn_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND YEAR(burn_time) = YEAR(CURDATE())")
    BigDecimal sumYearBurnArriveWeight(@Param("tenantId") String tenantId);

    /**
     * 当年燎毛后总重 kg（SUM burn_weight，燎毛记录当年；屠宰出品率分子）。
     *
     * @param tenantId 租户
     * @return SUM(burn_weight)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(burn_weight), 0) "
        + "  FROM t_warehouse_pig_burn_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND YEAR(burn_time) = YEAR(CURDATE())")
    BigDecimal sumYearBurnWeight(@Param("tenantId") String tenantId);

    /**
     * 当年白条入库总重 kg（SUM in_weight，白条信息表按 in_time 当年；白条出品率分母）。
     *
     * @param tenantId 租户
     * @return SUM(in_weight)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(in_weight), 0) "
        + "  FROM t_warehouse_bar_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND in_time IS NOT NULL "
        + "   AND YEAR(in_time) = YEAR(CURDATE())")
    BigDecimal sumYearBarInWeight(@Param("tenantId") String tenantId);

    /**
     * 当年白条出库总重 kg（SUM out_weight，白条信息表按 in_time 当年；白条出品率分子）。
     *
     * @param tenantId 租户
     * @return SUM(out_weight)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(out_weight), 0) "
        + "  FROM t_warehouse_bar_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND in_time IS NOT NULL "
        + "   AND YEAR(in_time) = YEAR(CURDATE())")
    BigDecimal sumYearBarOutWeight(@Param("tenantId") String tenantId);

    /**
     * 当年分割头数（COUNT 分割完成记录 cut_status='done'，按 cut_done_time 当年）。
     *
     * @param tenantId 租户
     * @return COUNT(*)，无记录返 0
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_pig_cut_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND cut_status = 'done' "
        + "   AND cut_done_time IS NOT NULL "
        + "   AND YEAR(cut_done_time) = YEAR(CURDATE())")
    Integer countYearCut(@Param("tenantId") String tenantId);

    /**
     * 当年分割领用总重 kg（SUM pickup_weight，分割完成记录当年；分割率分母）。
     *
     * @param tenantId 租户
     * @return SUM(pickup_weight)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(pickup_weight), 0) "
        + "  FROM t_warehouse_pig_cut_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND cut_status = 'done' "
        + "   AND cut_done_time IS NOT NULL "
        + "   AND YEAR(cut_done_time) = YEAR(CURDATE())")
    BigDecimal sumYearCutPickupWeight(@Param("tenantId") String tenantId);

    /**
     * 当年分割品产出总重 kg（SUM product_weight，过程产品表 white_bar_id 非空、按 produce_date 当年；分割率分子）。
     *
     * @param tenantId 租户
     * @return SUM(product_weight)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(product_weight), 0) "
        + "  FROM t_warehouse_product_inhouse "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND white_bar_id IS NOT NULL "
        + "   AND YEAR(produce_date) = YEAR(CURDATE())")
    BigDecimal sumYearCutProductWeight(@Param("tenantId") String tenantId);

    /**
     * 猪肉月度趋势：当年逐月送宰头数 + 屠宰出品率。
     *
     * <p>按 {@code MONTH(burn_time)} 聚合：name = 月份数字字符串（"1".."12"），
     * value = 当月送宰头数（COUNT）；另返当月到场总重/燎毛总重供 service 折算屠宰出品率。
     * 仅返回有记录的月份，service 按当年至今逐月补齐缺月为 0。</p>
     *
     * @param tenantId 租户
     * @return 各月聚合行（name=月份, value=送宰头数, extraA=到场总重, extraB=燎毛总重）
     */
    @Select("SELECT CAST(MONTH(burn_time) AS CHAR) AS name, "
        + "       COUNT(*) AS value, "
        + "       COALESCE(SUM(arrive_weight), 0) AS extraA, "
        + "       COALESCE(SUM(burn_weight), 0) AS extraB "
        + "  FROM t_warehouse_pig_burn_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND YEAR(burn_time) = YEAR(CURDATE()) "
        + " GROUP BY CAST(MONTH(burn_time) AS CHAR)")
    List<ProductionMonthRowVo> selectPigMonthlyTrend(@Param("tenantId") String tenantId);

    // ============================== 生产管理统计（果蔬 tab） ==============================

    /**
     * 当年果蔬处理批次数（COUNT 过程产品行，plot_id 非空、按 produce_date 当年）。
     *
     * @param tenantId 租户
     * @return COUNT(*)，无记录返 0
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_product_inhouse "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND plot_id IS NOT NULL "
        + "   AND YEAR(produce_date) = YEAR(CURDATE())")
    Integer countYearVeg(@Param("tenantId") String tenantId);

    /**
     * 当年果蔬处理总重 kg（SUM product_weight，过程产品 plot_id 非空、当年）。
     *
     * @param tenantId 租户
     * @return SUM(product_weight)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(product_weight), 0) "
        + "  FROM t_warehouse_product_inhouse "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND plot_id IS NOT NULL "
        + "   AND YEAR(produce_date) = YEAR(CURDATE())")
    BigDecimal sumYearVegWeight(@Param("tenantId") String tenantId);

    /**
     * 当年果蔬处理品种数（COUNT DISTINCT product_id，plot_id 非空、当年）。
     *
     * @param tenantId 租户
     * @return COUNT(DISTINCT product_id)，无记录返 0
     */
    @Select("SELECT COUNT(DISTINCT product_id) "
        + "  FROM t_warehouse_product_inhouse "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND plot_id IS NOT NULL "
        + "   AND YEAR(produce_date) = YEAR(CURDATE())")
    Integer countYearVegKind(@Param("tenantId") String tenantId);

    /**
     * 果蔬月度趋势：当年逐月处理批次数 + 处理总重。
     *
     * <p>name = 月份数字字符串，value = 当月批次数（COUNT），extraA = 当月处理总重，extraB = 0（占位）。
     * 仅返回有记录月份，service 补齐缺月。</p>
     *
     * @param tenantId 租户
     * @return 各月聚合行
     */
    @Select("SELECT CAST(MONTH(produce_date) AS CHAR) AS name, "
        + "       COUNT(*) AS value, "
        + "       COALESCE(SUM(product_weight), 0) AS extraA, "
        + "       0 AS extraB "
        + "  FROM t_warehouse_product_inhouse "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND plot_id IS NOT NULL "
        + "   AND YEAR(produce_date) = YEAR(CURDATE()) "
        + " GROUP BY CAST(MONTH(produce_date) AS CHAR)")
    List<ProductionMonthRowVo> selectVegMonthlyTrend(@Param("tenantId") String tenantId);

    // ============================== 生产管理统计（发货 tab） ==============================

    /**
     * 当年发货单数（COUNT 发货流水，按 ship_date 当年）。
     *
     * @param tenantId 租户
     * @return COUNT(*)，无记录返 0
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_shipment "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND YEAR(ship_date) = YEAR(CURDATE())")
    Integer countYearShipment(@Param("tenantId") String tenantId);

    /**
     * 当年发货总量（SUM ship_quantity，发货流水当年）。
     *
     * @param tenantId 租户
     * @return SUM(ship_quantity)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(ship_quantity), 0) "
        + "  FROM t_warehouse_shipment "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND YEAR(ship_date) = YEAR(CURDATE())")
    BigDecimal sumYearShipQuantity(@Param("tenantId") String tenantId);

    /**
     * 当年已送达单数（COUNT shipment_status='delivered'，按 ship_date 当年）。
     *
     * @param tenantId 租户
     * @return COUNT(*)，无记录返 0
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_shipment "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND shipment_status = 'delivered' "
        + "   AND YEAR(ship_date) = YEAR(CURDATE())")
    Integer countYearDelivered(@Param("tenantId") String tenantId);

    /**
     * 发货月度趋势：当年逐月发货单数 + 发货总量。
     *
     * <p>name = 月份数字字符串，value = 当月发货单数（COUNT），extraA = 当月发货总量，extraB = 0。
     * 仅返回有记录月份，service 补齐缺月。</p>
     *
     * @param tenantId 租户
     * @return 各月聚合行
     */
    @Select("SELECT CAST(MONTH(ship_date) AS CHAR) AS name, "
        + "       COUNT(*) AS value, "
        + "       COALESCE(SUM(ship_quantity), 0) AS extraA, "
        + "       0 AS extraB "
        + "  FROM t_warehouse_shipment "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND YEAR(ship_date) = YEAR(CURDATE()) "
        + " GROUP BY CAST(MONTH(ship_date) AS CHAR)")
    List<ProductionMonthRowVo> selectShipMonthlyTrend(@Param("tenantId") String tenantId);

    // ============================== 库位概览（summary 兼容） ==============================

    /**
     * 库位概览 Top 20（按库位名排序）。
     *
     * <p>status 在 SQL 内推导：库位停用（location_status=0）或当月该库位有盘点差异 →
     * abnormal，否则 normal。</p>
     *
     * @param tenantId 租户
     * @return 库位概览列表（最多 20 行）
     */
    @Select("SELECT l.id AS locationId, "
        + "       l.location_name AS locationName, "
        + "       l.location_type AS locationType, "
        + "       COALESCE(SUM(s.product_stock), 0) AS currentStock, "
        + "       CASE WHEN l.location_status = 0 "
        + "             OR EXISTS (SELECT 1 FROM t_warehouse_check_record c "
        + "                         WHERE c.tenant_id = l.tenant_id "
        + "                           AND c.del_flag = '0' "
        + "                           AND c.is_header = 0 "
        + "                           AND c.location_id = l.id "
        + "                           AND c.diff_stock <> 0 "
        + "                           AND c.check_date >= DATE_FORMAT(NOW(), '%Y-%m-01')) "
        + "            THEN 'abnormal' ELSE 'normal' END AS status "
        + "  FROM t_warehouse_location_info l "
        + "  LEFT JOIN t_warehouse_location_stock s "
        + "         ON s.location_id = l.id AND s.del_flag = '0' "
        + " WHERE l.tenant_id = #{tenantId} "
        + "   AND l.del_flag = '0' "
        + " GROUP BY l.id, l.location_name, l.location_type, l.location_status, l.tenant_id "
        + " ORDER BY l.location_name "
        + " LIMIT 20")
    List<LocationOverviewItemVo> selectLocationOverview(@Param("tenantId") String tenantId);

}
