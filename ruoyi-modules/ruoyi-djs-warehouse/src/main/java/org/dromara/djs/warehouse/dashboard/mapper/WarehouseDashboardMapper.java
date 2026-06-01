package org.dromara.djs.warehouse.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.dashboard.domain.vo.LocationOverviewItemVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 仓库看板聚合查询 Mapper（DJS-FIX-ADMIN-W22-006 占位版）。
 *
 * <p>本 mapper 不绑定单表实体；只提供 dashboard service 调用的原始聚合 SQL。
 * 数据源：{@code t_warehouse_demand_manage} + {@code t_warehouse_stock_flow}
 * + {@code t_warehouse_check_record} + {@code t_warehouse_location_info} +
 * {@code t_warehouse_location_stock}。</p>
 *
 * <p><b>关键约定（实际表结构 vs prompt 假设的偏差）</b>：</p>
 * <ul>
 *   <li>需求量取 {@code demand_quantity}（非 pig_count）；业态字段是
 *       {@code product_type} VARCHAR 值 {@code 'white_bar'}（WMS-DEMAND-001 已 ALTER 为 VARCHAR）。</li>
 *   <li>出入库方向用 {@code inout_type}（值 in/out），非 {@code flow_type='in'}。</li>
 *   <li>盘点表 {@code t_warehouse_check_record} 无 result_type 列；正常 / 异常 / 计损由
 *       {@code diff_stock} 推导：=0 正常 / !=0 异常 / &lt;0 计损（盘亏）。</li>
 *   <li>库位主表 {@code t_warehouse_location_info}（非 t_warehouse_location）；库存表
 *       {@code t_warehouse_location_stock.warehouse_id} FK 指向库位 id，库存列是 {@code product_stock}。</li>
 * </ul>
 *
 * <p>dashboard 聚合不走 BaseMapperPlus，故所有 SQL 显式 {@code WHERE tenant_id = #{tenantId}}
 * （参照 BRD-DASH-001 AggregateQueryMapper 范式）。</p>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-006
 */
@Mapper
public interface WarehouseDashboardMapper {

    /**
     * KPI 1：今日白条需求量合计。
     *
     * @param tenantId 租户
     * @return SUM(demand_quantity)，无记录返 0
     */
    @Select("SELECT COALESCE(SUM(demand_quantity), 0) "
        + "  FROM t_warehouse_demand_manage "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND product_type = 'white_bar' "
        + "   AND demand_date = CURDATE() "
        + "   AND del_flag = '0'")
    BigDecimal sumTodayWhiteBarDemand(@Param("tenantId") String tenantId);

    /**
     * KPI 2：今日生产入库笔数（inout_type='in'）。
     *
     * @param tenantId 租户
     * @return COUNT(*)，无记录返 0
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND inout_type = 'in' "
        + "   AND DATE(flow_date) = CURDATE() "
        + "   AND del_flag = '0'")
    Integer countTodayProduction(@Param("tenantId") String tenantId);

    /**
     * KPI 3：最近一天盘点记录数（diff_stock = 0 正常）。
     *
     * <p>"最近一天" = 该租户 check_record 的 {@code MAX(check_date)}。该天可能有多条
     * 盘点记录（按产品 / 库位拆），按 diff_stock 分桶。</p>
     *
     * @param tenantId 租户
     * @return diff_stock=0 的盘点条数
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_check_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND diff_stock = 0 "
        + "   AND check_date = (SELECT MAX(check_date) FROM t_warehouse_check_record "
        + "                      WHERE tenant_id = #{tenantId} AND del_flag = '0')")
    Integer countLatestCheckNormal(@Param("tenantId") String tenantId);

    /**
     * KPI 3：最近一天盘点异常数（diff_stock != 0）。
     *
     * @param tenantId 租户
     * @return diff_stock!=0 的盘点条数
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_check_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND diff_stock <> 0 "
        + "   AND check_date = (SELECT MAX(check_date) FROM t_warehouse_check_record "
        + "                      WHERE tenant_id = #{tenantId} AND del_flag = '0')")
    Integer countLatestCheckAbnormal(@Param("tenantId") String tenantId);

    /**
     * KPI 3：最近一天盘点计损数（diff_stock &lt; 0，盘亏）。
     *
     * @param tenantId 租户
     * @return diff_stock&lt;0 的盘点条数
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_warehouse_check_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND diff_stock < 0 "
        + "   AND check_date = (SELECT MAX(check_date) FROM t_warehouse_check_record "
        + "                      WHERE tenant_id = #{tenantId} AND del_flag = '0')")
    Integer countLatestCheckLoss(@Param("tenantId") String tenantId);

    /**
     * 当月异常库位数（COUNT DISTINCT warehouse_id WHERE diff_stock!=0 AND 当月）。
     *
     * @param tenantId 租户
     * @return 当月有盘点差异的不重复库位数
     */
    @Select("SELECT COUNT(DISTINCT warehouse_id) "
        + "  FROM t_warehouse_check_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND diff_stock <> 0 "
        + "   AND warehouse_id IS NOT NULL "
        + "   AND check_date >= DATE_FORMAT(NOW(), '%Y-%m-01')")
    Integer countMonthAbnormalLocation(@Param("tenantId") String tenantId);

    /**
     * 库位概览 Top 20（按库位名排序）。
     *
     * <p>status 在 SQL 内推导：库位停用（location_status=0）或当月该库位有盘点差异 →
     * abnormal，否则 normal。当月异常库位用相关子查询判断（数据量小，Top 20 可接受）。</p>
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
        + "                           AND c.warehouse_id = l.id "
        + "                           AND c.diff_stock <> 0 "
        + "                           AND c.check_date >= DATE_FORMAT(NOW(), '%Y-%m-01')) "
        + "            THEN 'abnormal' ELSE 'normal' END AS status "
        + "  FROM t_warehouse_location_info l "
        + "  LEFT JOIN t_warehouse_location_stock s "
        + "         ON s.warehouse_id = l.id AND s.del_flag = '0' "
        + " WHERE l.tenant_id = #{tenantId} "
        + "   AND l.del_flag = '0' "
        + " GROUP BY l.id, l.location_name, l.location_type, l.location_status, l.tenant_id "
        + " ORDER BY l.location_name "
        + " LIMIT 20")
    List<LocationOverviewItemVo> selectLocationOverview(@Param("tenantId") String tenantId);

}
