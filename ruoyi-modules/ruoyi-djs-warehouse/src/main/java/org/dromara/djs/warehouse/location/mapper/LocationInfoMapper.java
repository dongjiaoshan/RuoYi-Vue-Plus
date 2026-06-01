package org.dromara.djs.warehouse.location.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.domain.vo.LocationCardSummaryVo;
import org.dromara.djs.warehouse.location.domain.vo.LocationInfoVo;

import java.util.List;

/**
 * 库位 Mapper（WMS-MD-001）。
 *
 * @author djs
 * @since WMS-MD-001
 */
public interface LocationInfoMapper extends BaseMapperPlus<LocationInfo, LocationInfoVo> {

    /**
     * 按库位类型聚合 [库位数] + [在库产品数] + [当前库存总量]。
     *
     * <p>左联 location_stock：location 无库存时 productCount=0 / currentStock=0。
     * 多租户拦截器对自定义 {@code @Select} 含 JOIN 的聚合不保证注入 tenant_id，
     * 故 WHERE 显式带 {@code tenant_id = #{tenantId}}（DJS-FIX-ADMIN-W22-008 §0.5）。</p>
     *
     * @param tenantId 租户（V1 固定 '1001'）
     * @return 仅含 locationType / locationCount / productCount / currentStock 的部分聚合行
     */
    @Select("""
        SELECT l.location_type AS locationType,
               COUNT(DISTINCT l.id) AS locationCount,
               COUNT(DISTINCT ls.product_id) AS productCount,
               COALESCE(SUM(ls.product_stock), 0) AS currentStock
        FROM t_warehouse_location_info l
        LEFT JOIN t_warehouse_location_stock ls
               ON ls.location_id = l.id AND ls.del_flag = '0' AND ls.tenant_id = #{tenantId}
        WHERE l.del_flag = '0' AND l.tenant_id = #{tenantId}
        GROUP BY l.location_type
        """)
    List<LocationCardSummaryVo> selectStockSummaryByType(@Param("tenantId") String tenantId);

    /**
     * 按库位类型聚合今日 [入库量] + [出库量]（stock_flow.flow_date = 今天）。
     *
     * <p>stock_flow.warehouse_id 物理列名实为 location FK（doc/11 §2.3 命名遗留）。
     * inout_type: IN 入 / OT 出。WHERE 显式带 tenant_id（§0.5）。</p>
     *
     * @param tenantId 租户
     * @return 仅含 locationType / todayInQty / todayOutQty 的部分聚合行
     */
    @Select("""
        SELECT l.location_type AS locationType,
               COALESCE(SUM(CASE WHEN f.inout_type = 'IN' THEN ABS(f.change_quantity) ELSE 0 END), 0) AS todayInQty,
               COALESCE(SUM(CASE WHEN f.inout_type = 'OT' THEN ABS(f.change_quantity) ELSE 0 END), 0) AS todayOutQty
        FROM t_warehouse_stock_flow f
        JOIN t_warehouse_location_info l
          ON f.warehouse_id = l.id AND l.del_flag = '0' AND l.tenant_id = #{tenantId}
        WHERE f.del_flag = '0' AND f.tenant_id = #{tenantId}
          AND DATE(f.flow_date) = CURDATE()
        GROUP BY l.location_type
        """)
    List<LocationCardSummaryVo> selectTodayFlowByType(@Param("tenantId") String tenantId);

    /**
     * 按库位类型取最近一次盘点的 [盘点日] + [盘点结果]（每类取 latest_check_time 最大的库存行）。
     *
     * <p>用 NOT EXISTS 子查询取每类最近一条；WHERE 显式带 tenant_id（§0.5）。</p>
     *
     * @param tenantId 租户
     * @return 仅含 locationType / lastCheckDate / lastCheckResult 的部分聚合行
     */
    @Select("""
        SELECT l.location_type AS locationType,
               ls.latest_check_time AS lastCheckDate,
               ls.check_result AS lastCheckResult
        FROM t_warehouse_location_stock ls
        JOIN t_warehouse_location_info l
          ON ls.location_id = l.id AND l.del_flag = '0' AND l.tenant_id = #{tenantId}
        WHERE ls.del_flag = '0' AND ls.tenant_id = #{tenantId}
          AND ls.latest_check_time IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM t_warehouse_location_stock ls2
              JOIN t_warehouse_location_info l2 ON ls2.location_id = l2.id
              WHERE l2.location_type = l.location_type
                AND ls2.del_flag = '0' AND ls2.tenant_id = #{tenantId}
                AND ls2.latest_check_time IS NOT NULL
                AND ls2.latest_check_time > ls.latest_check_time
          )
        """)
    List<LocationCardSummaryVo> selectLastCheckByType(@Param("tenantId") String tenantId);

}

