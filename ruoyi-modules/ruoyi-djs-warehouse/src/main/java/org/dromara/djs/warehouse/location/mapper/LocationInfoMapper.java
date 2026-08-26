package org.dromara.djs.warehouse.location.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueLocationVo;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.domain.vo.LocationCardSummaryVo;
import org.dromara.djs.warehouse.location.domain.vo.LocationInfoVo;
import org.dromara.djs.warehouse.location.domain.vo.LocationProductStockVo;

import java.util.List;

/**
 * 库位 Mapper（WMS-MD-001）。
 *
 * @author djs
 * @since WMS-MD-001
 */
public interface LocationInfoMapper extends BaseMapperPlus<LocationInfo, LocationInfoVo> {

    /**
     * 按单个库位聚合 [在库产品数] + [当前库存总量]（每库位一行）。
     *
     * <p>左联 location_stock：location 无库存时 productCount=0 / currentStock=0。
     * 多租户拦截器对自定义 {@code @Select} 含 JOIN 的聚合不保证注入 tenant_id，
     * 故 WHERE 显式带 {@code tenant_id = #{tenantId}}（§0.5）。返回库位身份字段供前端逐库位展示。</p>
     *
     * @param tenantId 租户（V1 固定 '1001'）
     * @return 按库位聚合行（locationId / locationCode / locationName / locationType / productCount / currentStock）
     */
    @Select("""
        SELECT l.id AS locationId,
               l.location_code AS locationCode,
               l.location_name AS locationName,
               l.location_type AS locationType,
               COUNT(DISTINCT ls.product_id) AS productCount,
               COALESCE(SUM(ls.product_stock), 0) AS currentStock
        FROM t_warehouse_location_info l
        LEFT JOIN t_warehouse_location_stock ls
               ON ls.location_id = l.id AND ls.del_flag = '0' AND ls.tenant_id = #{tenantId}
        WHERE l.del_flag = '0' AND l.tenant_id = #{tenantId}
        GROUP BY l.id, l.location_code, l.location_name, l.location_type
        """)
    List<LocationCardSummaryVo> selectStockSummaryByLocation(@Param("tenantId") String tenantId);

    /**
     * 按单个库位聚合今日 [入库量] + [出库量]（stock_flow.flow_date = 今天）。
     *
     * <p>stock_flow.warehouse_id 物理列名实为 location FK（doc/11 §2.3 命名遗留）。
     * inout_type: IN 入 / OT 出。WHERE 显式带 tenant_id（§0.5）。</p>
     *
     * <p>今日出库只统计真实出库（发货/领用/分割/发货等），<b>排除录入损耗</b>
     * （flow_type='loss' 也记 inout_type='OT'，属库存缩减非出库，不计入今日出库）。</p>
     *
     * @param tenantId 租户
     * @return 仅含 locationId / todayInQty / todayOutQty 的部分聚合行
     */
    @Select("""
        SELECT l.id AS locationId,
               COALESCE(SUM(CASE WHEN f.inout_type = 'IN' THEN ABS(f.change_quantity) ELSE 0 END), 0) AS todayInQty,
               COALESCE(SUM(CASE WHEN f.inout_type = 'OT' AND f.flow_type <> 'loss' THEN ABS(f.change_quantity) ELSE 0 END), 0) AS todayOutQty
        FROM t_warehouse_stock_flow f
        JOIN t_warehouse_location_info l
          ON f.warehouse_id = l.id AND l.del_flag = '0' AND l.tenant_id = #{tenantId}
        WHERE f.del_flag = '0' AND f.tenant_id = #{tenantId}
          AND DATE(f.flow_date) = CURDATE()
        GROUP BY l.id
        """)
    List<LocationCardSummaryVo> selectTodayFlowByLocation(@Param("tenantId") String tenantId);

    /**
     * 按单个库位取最近一次盘点的 [盘点日] + [盘点结果]（每库位取 latest_check_time 最大的库存行）。
     *
     * <p>用 NOT EXISTS 子查询取每库位最近一条；WHERE 显式带 tenant_id（§0.5）。</p>
     *
     * @param tenantId 租户
     * @return 仅含 locationId / lastCheckDate / lastCheckResult 的部分聚合行
     */
    @Select("""
        SELECT ls.location_id AS locationId,
               ls.latest_check_time AS lastCheckDate,
               ls.check_result AS lastCheckResult
        FROM t_warehouse_location_stock ls
        JOIN t_warehouse_location_info l
          ON ls.location_id = l.id AND l.del_flag = '0' AND l.tenant_id = #{tenantId}
        WHERE ls.del_flag = '0' AND ls.tenant_id = #{tenantId}
          AND ls.latest_check_time IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM t_warehouse_location_stock ls2
              WHERE ls2.location_id = ls.location_id
                AND ls2.del_flag = '0' AND ls2.tenant_id = #{tenantId}
                AND ls2.latest_check_time IS NOT NULL
                AND ls2.latest_check_time > ls.latest_check_time
          )
        """)
    List<LocationCardSummaryVo> selectLastCheckByLocation(@Param("tenantId") String tenantId);

    /**
     * 单库位内的逐产品库存明细（V6 row136 卡片下钻抽屉）。
     *
     * <p>产品集合取「当前有库存行的产品」∪「今天在该库位有流水的产品」——只取前者的话，
     * 当天进完又出完（库存归零）的产品会整行消失，「今日入库 / 今日出库」两列就看不到当天最活跃的货；
     * 只取后者又漏掉了往日囤着没动的存货。</p>
     *
     * <p>{@code stock_flow.warehouse_id} 物理列名实为 location FK（doc/11 §2.3 命名遗留）。
     * 今日出库排除 {@code flow_type='loss'}，与库位卡片 {@link #selectTodayFlowByLocation} 同口径。
     * 多租户拦截器对含 JOIN 的自定义 {@code @Select} 不保证注入，WHERE 全部显式带 tenant_id（§0.5）。</p>
     *
     * @param tenantId    租户（V1 固定 '1001'）
     * @param locationId  库位 ID
     * @param productName 产品名称模糊搜索（空 = 不过滤）
     * @return 逐产品行，按实时库存量倒序、同量按产品名
     */
    @Select("""
        SELECT t.product_id       AS productId,
               p.product_name     AS productName,
               p.product_spec     AS productSpec,
               p.product_unit     AS productUnit,
               COALESCE(s.stock, 0)   AS productStock,
               COALESCE(f.in_qty, 0)  AS todayInQty,
               COALESCE(f.out_qty, 0) AS todayOutQty
        FROM (
            SELECT product_id
              FROM t_warehouse_location_stock
             WHERE del_flag = '0' AND tenant_id = #{tenantId} AND location_id = #{locationId}
               AND product_id IS NOT NULL
             GROUP BY product_id
            UNION
            SELECT product_id
              FROM t_warehouse_stock_flow
             WHERE del_flag = '0' AND tenant_id = #{tenantId} AND warehouse_id = #{locationId}
               AND product_id IS NOT NULL AND DATE(flow_date) = CURDATE()
             GROUP BY product_id
        ) t
        JOIN t_warehouse_product_info p
          ON p.id = t.product_id AND p.del_flag = '0' AND p.tenant_id = #{tenantId}
        LEFT JOIN (
            SELECT product_id, SUM(product_stock) AS stock
              FROM t_warehouse_location_stock
             WHERE del_flag = '0' AND tenant_id = #{tenantId} AND location_id = #{locationId}
             GROUP BY product_id
        ) s ON s.product_id = t.product_id
        LEFT JOIN (
            SELECT product_id,
                   SUM(CASE WHEN inout_type = 'IN' THEN ABS(change_quantity) ELSE 0 END) AS in_qty,
                   SUM(CASE WHEN inout_type = 'OT' AND flow_type <> 'loss' THEN ABS(change_quantity) ELSE 0 END) AS out_qty
              FROM t_warehouse_stock_flow
             WHERE del_flag = '0' AND tenant_id = #{tenantId} AND warehouse_id = #{locationId}
               AND DATE(flow_date) = CURDATE()
             GROUP BY product_id
        ) f ON f.product_id = t.product_id
        WHERE (#{productName} IS NULL OR p.product_name LIKE CONCAT('%', #{productName}, '%'))
        ORDER BY COALESCE(s.stock, 0) DESC, p.product_name
        """)
    List<LocationProductStockVo> selectProductStockByLocation(@Param("tenantId") String tenantId,
                                                              @Param("locationId") Long locationId,
                                                              @Param("productName") String productName);

    /**
     * mp 物资领用「按库位类型列库位 chip」列表（WMS-OUTSOURCE-001：crop_loc 种植库 / farm_loc 养殖库）。
     *
     * <p>与按 belong_type 组织的 {@code selectMatIssueLocations} 对称，本方法直接按 {@code location_type}
     * 列出该类型下全部未软删库位，按 {@code location_sort} 升序（对齐原型「库位 chip 按排序码」）。
     * 租户单租户显式 {@code tenant_id='1001'}（V1）。</p>
     *
     * @param locationType 字典 {@code djs_location_type} 的 value（{@code crop_loc} / {@code farm_loc}）
     * @return 该类型库位 chip 列表（按 location_sort、id 升序）；无则空 list
     */
    @Select("""
        SELECT id            AS locationId,
               location_code AS locationCode,
               location_name AS locationName
          FROM t_warehouse_location_info
         WHERE location_type = #{locationType}
           AND del_flag      = '0'
           AND tenant_id     = '1001'
         ORDER BY location_sort ASC, id ASC
        """)
    List<MatIssueLocationVo> selectMatIssueLocationsByType(@Param("locationType") String locationType);

}

