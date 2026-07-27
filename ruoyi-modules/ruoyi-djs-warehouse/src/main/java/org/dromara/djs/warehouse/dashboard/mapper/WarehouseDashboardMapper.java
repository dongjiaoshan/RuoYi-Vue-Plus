package org.dromara.djs.warehouse.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.dashboard.domain.vo.ChartSeriesItemVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.ChartTrendPointVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.LocationOverviewItemVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 仓库看板聚合查询 Mapper。
 *
 * <p>本 mapper 不绑定单表实体；只提供 dashboard service 调用的原始聚合 SQL。
 * 数据源：{@code t_warehouse_demand_manage} + {@code t_store_return}
 * + {@code t_warehouse_product_production} + {@code t_warehouse_stock_flow}
 * + {@code t_warehouse_check_record} + {@code t_warehouse_location_info} +
 * {@code t_warehouse_location_stock}。</p>
 *
 * <p><b>关键列约定（按实库结构）</b>：</p>
 * <ul>
 *   <li>需求表业态字段 {@code product_type} VARCHAR(32)，值 white_bar/vegetable/gift_box/other
 *       （字典 djs_demand_product_type）；需求量列 {@code demand_quantity}。</li>
 *   <li>退货表 {@code t_store_return}（STORE-RETURN-UNIFY 后的退货单一真相源）：方向列
 *       {@code return_direction}（customer_to_store / store_to_warehouse / warehouse_to_supplier，
 *       字典 djs_return_direction）；数量列 {@code return_quantity}。</li>
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
     * 今日某产品归属类型（belong_type）需求量合计。
     *
     * <p>需求表只落 4 业态（product_type），细分品类（pork/dry_good/egg）靠 JOIN 产品主数据按
     * {@code belong_type} 聚合。无数据 / 无该归属产品 → 0。</p>
     *
     * @param tenantId   租户
     * @param belongType 产品归属类型（pork / vegetable / dry_good / egg / gift_box / white_bar）
     * @return SUM(demand_quantity)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(d.demand_quantity), 0) "
        + "  FROM t_warehouse_demand_manage d "
        + "  JOIN t_warehouse_product_info p ON p.id = d.product_id AND p.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND p.belong_type = #{belongType} "
        + "   AND d.demand_date = CURDATE() "
        + "   AND d.del_flag = '0'")
    BigDecimal sumTodayDemandByBelong(@Param("tenantId") String tenantId, @Param("belongType") String belongType);

    /**
     * 今日某产品归属类型（belong_type）需求涉及的不重复产品品类数。
     *
     * @param tenantId   租户
     * @param belongType 产品归属类型
     * @return COUNT(DISTINCT d.product_id)，无记录返 0
     */
    @Select("SELECT COUNT(DISTINCT d.product_id) "
        + "  FROM t_warehouse_demand_manage d "
        + "  JOIN t_warehouse_product_info p ON p.id = d.product_id AND p.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND p.belong_type = #{belongType} "
        + "   AND d.demand_date = CURDATE() "
        + "   AND d.del_flag = '0'")
    Integer countTodayDemandKindsByBelong(@Param("tenantId") String tenantId, @Param("belongType") String belongType);

    /**
     * 今日「其他产品」需求品种数（非白条业态、且产品归属类型非 pork/vegetable/white_bar 的去重产品数）。
     *
     * <p>白条按业态 {@code product_type='white_bar'} 单列（另计头数），故此处按 {@code d.product_type<>'white_bar'}
     * 排除；剩余产品按主数据 {@code belong_type} 排除 pork/vegetable/white_bar，其余（gift_box/egg/dry_good/未映射）
     * 统归「其他」。LEFT JOIN 使无主数据映射（belong_type NULL）的需求也计入「其他」。</p>
     *
     * @param tenantId 租户
     * @return COUNT(DISTINCT d.product_id)，无记录返 0
     */
    @Select("SELECT COUNT(DISTINCT d.product_id) "
        + "  FROM t_warehouse_demand_manage d "
        + "  LEFT JOIN t_warehouse_product_info p ON p.id = d.product_id AND p.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND d.demand_date = CURDATE() "
        + "   AND d.product_type <> 'white_bar' "
        + "   AND (p.belong_type IS NULL OR p.belong_type NOT IN ('pork', 'vegetable', 'white_bar'))")
    Integer countTodayDemandOtherKinds(@Param("tenantId") String tenantId);

    /**
     * 今日白条需求头数（需求条目数，白条业态一条需求 = 一头/批，按 SUM(demand_quantity) 计头）。
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
    BigDecimal sumTodayWhiteBarHeads(@Param("tenantId") String tenantId);

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

    /**
     * 今日送宰猪只头数（自产）：当天在养殖端选择送宰后即计入，无需等待燎毛间称重。
     *
     * <p>出栏事件会立即镜像一行 {@code bar_info(status=pending_singe)} 并写
     * {@code marketing_time}；此时 {@code in_time/arrive_weight} 尚为空。故仓库总览的“送宰猪只”
     * 必须按出栏选择时间统计，而不能套用日统计“已屠宰称重”的口径。外购白条 {@code buy_date} 非空，
     * 不属于养殖端送宰选择，排除。</p>
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_bar_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND DATE(marketing_time) = CURDATE() "
        + "   AND buy_date IS NULL "
        + "   AND del_flag = '0'")
    Integer countTodaySlaughterPigs(@Param("tenantId") String tenantId);

    /**
     * 今日白条总重（燎毛后入库重量合计 kg）。
     *
     * @param tenantId 租户
     * @return SUM(burn_weight)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(burn_weight), 0) "
        + "  FROM t_warehouse_pig_burn_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND DATE(burn_time) = CURDATE() "
        + "   AND del_flag = '0'")
    BigDecimal sumTodayWhiteBarWeight(@Param("tenantId") String tenantId);

    /**
     * 今日出栏猪只总重（白条主表 marketing_weight 合计 kg，按出栏时间 marketing_time 落当天计）。
     *
     * @param tenantId 租户
     * @return SUM(marketing_weight) WHERE DATE(marketing_time)=CURDATE()，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(marketing_weight), 0) "
        + "  FROM t_warehouse_bar_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND DATE(marketing_time) = CURDATE() "
        + "   AND del_flag = '0'")
    BigDecimal sumTodayMarketingWeight(@Param("tenantId") String tenantId);

    /**
     * 今日毛菜处理果蔬品种数（毛菜处理流水 record_type=1 已称重记录涉及的不重复作物数）。
     *
     * @param tenantId 租户
     * @return COUNT(DISTINCT crop_id) WHERE record_type=1 AND is_weighed=1，无记录返 0
     */
    @Select("SELECT COUNT(DISTINCT crop_id) "
        + "  FROM t_warehouse_handle_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND record_type = 1 "
        + "   AND is_weighed = 1 "
        + "   AND DATE(handle_time) = CURDATE() "
        + "   AND del_flag = '0'")
    Integer countTodayVegHandleKinds(@Param("tenantId") String tenantId);

    /**
     * 今日毛菜处理果蔬总重量（毛菜处理流水 record_type=1 已称重 record_weight 合计 kg）。
     *
     * @param tenantId 租户
     * @return SUM(record_weight) WHERE record_type=1 AND is_weighed=1，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(record_weight), 0) "
        + "  FROM t_warehouse_handle_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND record_type = 1 "
        + "   AND is_weighed = 1 "
        + "   AND DATE(handle_time) = CURDATE() "
        + "   AND del_flag = '0'")
    BigDecimal sumTodayVegHandleWeight(@Param("tenantId") String tenantId);

    /**
     * 今日分割白条数（当日转入分割车间白条数；半只计 0.5、整只计 1，row195 客户口径，按 pickup_time 当天计）。
     *
     * @param tenantId 租户
     * @return Σ CASE is_half(1→0.5 / 2→1) WHERE DATE(pickup_time)=CURDATE() AND white_bar_id 非空，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(CASE WHEN is_half = 1 THEN 0.5 WHEN is_half = 2 THEN 1 ELSE 1 END), 0) "
        + "  FROM t_warehouse_pig_cut_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND DATE(pickup_time) = CURDATE() "
        + "   AND white_bar_id IS NOT NULL "
        + "   AND out_type = 'cut' "
        + "   AND del_flag = '0'")
    BigDecimal countTodayCutBars(@Param("tenantId") String tenantId);

    /**
     * 今日白条分割产品总重：当天分割间入库流水 {@code cut_out_in} 的重量之和。
     *
     * <p>分割产品在白条分割页确认入库时即写不可变库存流水；后续是否打包、是否有耳号均不影响本指标。
     * 该口径与仓库日统计 {@code WarehouseStatAggregateMapper.sumCutProductWeight} 同源。</p>
     */
    @Select("SELECT COALESCE(SUM(change_quantity), 0) "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND flow_type = 'cut_out_in' "
        + "   AND DATE(flow_date) = CURDATE() "
        + "   AND del_flag = '0'")
    BigDecimal sumTodayCutProductWeight(@Param("tenantId") String tenantId);

    /**
     * 今日果蔬月台接收品种数（自产收货 receive_type=1 且已入库重量>0，涉及的不重复地块/产品品种数）。
     *
     * @param tenantId 租户
     * @return COUNT(DISTINCT COALESCE(plot_id, product_id))，无记录返 0
     */
    @Select("SELECT COUNT(DISTINCT COALESCE(plot_id, product_id)) "
        + "  FROM t_warehouse_veg_receive "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND receive_type = 1 "
        + "   AND weight > 0 "
        + "   AND DATE(receive_time) = CURDATE() "
        + "   AND del_flag = '0'")
    Integer countTodayVegReceiveKinds(@Param("tenantId") String tenantId);

    /**
     * 今日果蔬月台接收总重（自产收货 receive_type=1 且已入库重量>0 的重量合计 kg）。
     *
     * @param tenantId 租户
     * @return SUM(weight)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(weight), 0) "
        + "  FROM t_warehouse_veg_receive "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND receive_type = 1 "
        + "   AND weight > 0 "
        + "   AND DATE(receive_time) = CURDATE() "
        + "   AND del_flag = '0'")
    BigDecimal sumTodayVegReceiveWeight(@Param("tenantId") String tenantId);

    /**
     * 今日果蔬产品种类数（来源地块非空的生产记录涉及的不重复产品种类 = 果蔬产品产出）。
     *
     * @param tenantId 租户
     * @return COUNT(DISTINCT product_id) WHERE plot_id IS NOT NULL，无记录返 0
     */
    @Select("SELECT COUNT(DISTINCT product_id) "
        + "  FROM t_warehouse_product_production "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND plot_id IS NOT NULL "
        + "   AND produce_date = CURDATE() "
        + "   AND del_flag = '0'")
    Integer countTodayVegProductKinds(@Param("tenantId") String tenantId);

    /**
     * 今日果蔬产品总重（来源地块非空的生产记录重量合计 kg）。
     *
     * @param tenantId 租户
     * @return SUM(product_weight) WHERE plot_id IS NOT NULL，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(product_weight), 0) "
        + "  FROM t_warehouse_product_production "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND plot_id IS NOT NULL "
        + "   AND produce_date = CURDATE() "
        + "   AND del_flag = '0'")
    BigDecimal sumTodayVegProductWeight(@Param("tenantId") String tenantId);

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

    /**
     * 图⑤（细分）：当月盘点异常库位按库位名分布（环，对齐原型「当月盘点异常库位分布」）。
     *
     * <p>当月（check_date >= 当月 1 号）有盘点差异（diff_stock!=0）的明细，按库位名聚合异常明细数 COUNT，
     * 返回库位名作为 name（雪花库位 ID 已映射为名称，前端直接渲染）。</p>
     *
     * @param tenantId 租户
     * @return 各异常库位 name(=location_name) + value(=异常明细数)
     */
    @Select("SELECT l.location_name AS name, COUNT(*) AS value "
        + "  FROM t_warehouse_check_record c "
        + "  JOIN t_warehouse_location_info l ON l.id = c.location_id AND l.del_flag = '0' "
        + " WHERE c.tenant_id = #{tenantId} "
        + "   AND c.del_flag = '0' "
        + "   AND c.is_header = 0 "
        + "   AND c.diff_stock <> 0 "
        + "   AND c.check_date >= DATE_FORMAT(NOW(), '%Y-%m-01') "
        + " GROUP BY l.id, l.location_name "
        + " ORDER BY value DESC "
        + " LIMIT 12")
    List<ChartSeriesItemVo> selectMonthAbnormalLocationByName(@Param("tenantId") String tenantId);

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
     * <p>方向值 customer_to_store（顾客退店，insertByBo 单条直登）/ store_to_warehouse（门店退仓，
     * batchCreate 主链路）/ warehouse_to_supplier（占位）；不过滤确认态（pending 也计为退货事件）。</p>
     *
     * @param tenantId 租户
     * @return 各方向 name(=return_direction) + value(=COUNT)
     */
    @Select("SELECT return_direction AS name, COUNT(*) AS value "
        + "  FROM t_store_return "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) "
        + " GROUP BY return_direction")
    List<ChartSeriesItemVo> selectReturnByDirection(@Param("tenantId") String tenantId);

    /**
     * 图①（细分）：近 30 日某归属类型需求按产品名分布（果蔬切片：小白菜 / 大白菜 / ...）。
     *
     * <p>对齐原型「果蔬产品当日需求分布」饼图：按 belong_type 过滤 + 按产品名聚合 SUM(demand_quantity)，
     * 退货环「猪肉 / 果蔬切换」也复用本方法（传 pork / vegetable）。返回产品名作为 name 由前端直接渲染。</p>
     *
     * @param tenantId   租户
     * @param belongType 产品归属类型（vegetable / pork / ...）
     * @return 各产品 name(=product_name) + value(=SUM demand_quantity)
     */
    @Select("SELECT p.product_name AS name, COALESCE(SUM(d.demand_quantity), 0) AS value "
        + "  FROM t_warehouse_demand_manage d "
        + "  JOIN t_warehouse_product_info p ON p.id = d.product_id AND p.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND p.belong_type = #{belongType} "
        + "   AND d.del_flag = '0' "
        + "   AND d.demand_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) "
        + " GROUP BY p.id, p.product_name "
        + " ORDER BY value DESC "
        + " LIMIT 12")
    List<ChartSeriesItemVo> selectDemandByProductName(@Param("tenantId") String tenantId, @Param("belongType") String belongType);

    /**
     * 图②（细分）：近 7 日某归属类型退货按产品名构成（退货环「猪肉 / 果蔬」切换）。
     *
     * <p>对齐原型「退货产品分布(近7日)」环：默认猪肉（belong_type=pork，梅花肉 / 排骨 / 猪腿肉 / 猪五花 / 其他），
     * 可切换果蔬（vegetable）。按产品名聚合退货次数 COUNT，返回产品名作为 name。
     * 猪肉全量返回、果蔬由前端取 TOP5 其余归「其他」，故此处不加 LIMIT。</p>
     *
     * @param tenantId   租户
     * @param belongType 产品归属类型（pork / vegetable）
     * @return 各产品 name(=product_name) + value(=COUNT)，按值降序
     */
    @Select("SELECT p.product_name AS name, COUNT(*) AS value, MAX(p.product_unit) AS unit "
        + "  FROM t_store_return r "
        + "  JOIN t_warehouse_product_info p ON p.id = r.product_id AND p.del_flag = '0' "
        + " WHERE r.tenant_id = #{tenantId} "
        + "   AND p.belong_type = #{belongType} "
        + "   AND r.del_flag = '0' "
        + "   AND r.create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) "
        + " GROUP BY p.id, p.product_name "
        + " ORDER BY value DESC")
    List<ChartSeriesItemVo> selectReturnByProductName(@Param("tenantId") String tenantId, @Param("belongType") String belongType);

    /**
     * 图①（细分·近 7 日）：近 7 日某归属类型需求按产品名分布（明日产品需求分布饼「猪肉 / 果蔬」切换）。
     *
     * <p>对齐原型「明日产品需求分布（近7日）」饼：按 belong_type 过滤 + 按产品名聚合 SUM(demand_quantity)，
     * 前端「猪肉 / 果蔬」切换。猪肉全量返回、果蔬由前端取 TOP5 其余归「其他」，故此处不加 LIMIT。</p>
     *
     * @param tenantId   租户
     * @param belongType 产品归属类型（pork / vegetable）
     * @return 各产品 name(=product_name) + value(=SUM demand_quantity)，按值降序
     */
    @Select("SELECT p.product_name AS name, COALESCE(SUM(d.demand_quantity), 0) AS value, MAX(p.product_unit) AS unit "
        + "  FROM t_warehouse_demand_manage d "
        + "  JOIN t_warehouse_product_info p ON p.id = d.product_id AND p.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND p.belong_type = #{belongType} "
        + "   AND d.del_flag = '0' "
        + "   AND d.demand_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) "
        + " GROUP BY p.id, p.product_name "
        + " ORDER BY value DESC")
    List<ChartSeriesItemVo> selectDemandByProductName7d(@Param("tenantId") String tenantId, @Param("belongType") String belongType);

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
     * 图③（组合）：近 N 日白条头数日聚合（柱）。
     *
     * <p>对齐原型「产品生产趋势」组合图主柱：燎毛记录 = 当日白条头数 COUNT。</p>
     *
     * @param tenantId 租户
     * @param days     近几日
     * @return 各日 date + value(=白条头数)
     */
    @Select("SELECT DATE_FORMAT(burn_time, '%Y-%m-%d') AS date, COUNT(*) AS value "
        + "  FROM t_warehouse_pig_burn_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND burn_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
        + " GROUP BY DATE_FORMAT(burn_time, '%Y-%m-%d') "
        + " ORDER BY date")
    List<ChartTrendPointVo> selectWhiteBarHeadTrend(@Param("tenantId") String tenantId, @Param("days") int days);

    /**
     * 图③（组合）：近 N 日某归属类型生产重量日聚合（线，猪肉 / 果蔬）。
     *
     * @param tenantId   租户
     * @param days       近几日
     * @param belongType 产品归属类型（pork / vegetable）
     * @return 各日 date + value(=SUM product_weight)
     */
    @Select("SELECT DATE_FORMAT(pr.produce_date, '%Y-%m-%d') AS date, COALESCE(SUM(pr.product_weight), 0) AS value "
        + "  FROM t_warehouse_product_production pr "
        + "  JOIN t_warehouse_product_info p ON p.id = pr.product_id AND p.del_flag = '0' "
        + " WHERE pr.tenant_id = #{tenantId} "
        + "   AND pr.del_flag = '0' "
        + "   AND p.belong_type = #{belongType} "
        + "   AND pr.produce_date >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
        + " GROUP BY DATE_FORMAT(pr.produce_date, '%Y-%m-%d') "
        + " ORDER BY date")
    List<ChartTrendPointVo> selectProductionWeightTrendByBelong(@Param("tenantId") String tenantId,
        @Param("days") int days, @Param("belongType") String belongType);

    /**
     * 图④：近 7 日盘点结果分布（按盘点流水 flow_type 计数）。
     *
     * <p>数据源与「盘点记录」列表同源 = {@code t_warehouse_stock_flow} 盘点流水（含 mp 自助盘点，
     * admin 建单链 {@code t_warehouse_check_record} 覆盖不全，mp 现场盘点只写 stock_flow）。
     * 近 7 日（flow_date >= 当天前 7 天）按 flow_type → 结果映射计数：
     * {@code check_in}=正常(1) / {@code check_abnormal_out}=异常(2) / {@code check_out}=计损(3)。
     * 返回结果数值字符串作为 name，由 service 映射中文。</p>
     *
     * @param tenantId 租户
     * @return name(=1/2/3) + value(=COUNT)
     */
    @Select("SELECT CAST("
        + "         CASE flow_type "
        + "           WHEN 'check_in' THEN 1 "
        + "           WHEN 'check_abnormal_out' THEN 2 "
        + "           WHEN 'check_out' THEN 3 "
        + "         END AS CHAR) AS name, COUNT(*) AS value "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND flow_type IN ('check_in', 'check_out', 'check_abnormal_out') "
        + "   AND flow_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) "
        + " GROUP BY flow_type")
    List<ChartSeriesItemVo> selectCheckResult(@Param("tenantId") String tenantId);

    /**
     * 图⑥：近 N 日损耗趋势（损耗记录台账 t_warehouse_loss_flow 按 loss_date 日聚合 SUM loss_weight）。
     *
     * <p>仅返回有记录的日期，service 层按近 N 日补齐缺口日为 0。</p>
     *
     * @param tenantId 租户
     * @param days     近几日
     * @return 各日 date(yyyy-MM-dd) + value(=SUM loss_weight)
     */
    @Select("SELECT DATE_FORMAT(loss_date, '%Y-%m-%d') AS date, COALESCE(SUM(loss_weight), 0) AS value "
        + "  FROM t_warehouse_loss_flow "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND loss_date >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
        + " GROUP BY DATE_FORMAT(loss_date, '%Y-%m-%d') "
        + " ORDER BY date")
    List<ChartTrendPointVo> selectLossTrend(@Param("tenantId") String tenantId, @Param("days") int days);

    /**
     * 图⑥（细分）：近 N 日某归属类型损耗量日聚合（损耗趋势多系列：猪肉 / 果蔬）。
     *
     * <p>取统一损耗台账 t_warehouse_loss_flow 自带的 belong_type / loss_weight，按归属类型
     * SUM(loss_weight) 出「损耗量」（非率），口径与「损耗总览」页一致。各损耗环节
     * （毛菜 / 运输 / 盘点计损 / 白条预冷分割 / 生产 / 物资 / 燎毛）都双写这张台账；
     * stock_flow 的 flow_type='loss' 不是统一损耗源、只含极少量损耗，故旧图恒近 0。</p>
     *
     * @param tenantId   租户
     * @param days       近几日
     * @param belongType 产品归属类型（pork / vegetable）
     * @return 各日 date + value(=SUM loss_weight)
     */
    @Select("SELECT DATE_FORMAT(loss_date, '%Y-%m-%d') AS date, COALESCE(SUM(loss_weight), 0) AS value "
        + "  FROM t_warehouse_loss_flow "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND belong_type = #{belongType} "
        + "   AND loss_date >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
        + " GROUP BY DATE_FORMAT(loss_date, '%Y-%m-%d') "
        + " ORDER BY date")
    List<ChartTrendPointVo> selectLossTrendByBelong(@Param("tenantId") String tenantId,
        @Param("days") int days, @Param("belongType") String belongType);

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
