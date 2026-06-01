package org.dromara.djs.warehouse.stock.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.flow.domain.vo.PackingItemVo;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.domain.vo.LocationStockVo;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 库存明细 Mapper（WMS-MD-001 / WMS-PIG-001 扩展）。
 *
 * @author djs
 * @since WMS-MD-001
 */
public interface LocationStockMapper extends BaseMapperPlus<LocationStock, LocationStockVo> {

    /**
     * 统计指定库位下"仍有库存"（{@code product_stock > 0} 且未软删）的记录数。
     *
     * <p>由 LocationInfoServiceImpl#deleteWithValidByIds 删除前校验使用。
     * 不走 LambdaQueryWrapper：BigDecimal 比较 + del_flag 判断 + tenant 走拦截器，
     * 写原生 SQL 更清晰；tenant_id 由 MP 拦截器自动注入，此处无需显式 {@code WHERE tenant_id=?}。</p>
     */
    @org.apache.ibatis.annotations.Select(
        "SELECT COUNT(1) FROM t_warehouse_location_stock "
            + "WHERE location_id = #{locationId} "
            + "  AND product_stock > 0 "
            + "  AND del_flag = '0'")
    long countActiveStockByLocation(@Param("locationId") Long locationId);

    /**
     * 按 {@code ear_no} + {@code location_id} 原子扣减白条库存（WMS-PIG-001 燎毛工序）。
     *
     * <p>核心契约：</p>
     * <ul>
     *   <li>SQL 在 {@code WHERE} 加 {@code product_stock >= deductQty} —— MySQL 行锁 + 数量校验同步发生，
     *       并发提交（同 ear_no 两次燎毛）只有一次 affectedRows > 0</li>
     *   <li>{@code tenant_id} 由 MP 多租户拦截器在 final SQL 阶段注入，应用层无需显式 WHERE</li>
     *   <li>不显式触发 MP {@code updateFill}（{@code DjsMetaObjectHandler}），因此手工 set {@code update_by} / {@code update_time}</li>
     * </ul>
     *
     * @param locationId 库位 ID
     * @param earNo      猪只耳号
     * @param deductQty  扣减数量（必须 &gt; 0）
     * @param userId     操作人（写入 update_by 字段）
     * @return affectedRows（0 = 库存不足 / 耳号不匹配 / 已软删）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = product_stock - #{deductQty},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE location_id = #{locationId} "
        + "   AND ear_no = #{earNo} "
        + "   AND product_stock >= #{deductQty} "
        + "   AND del_flag = '0'")
    int deductByEarNo(@Param("locationId") Long locationId,
                      @Param("earNo") String earNo,
                      @Param("deductQty") BigDecimal deductQty,
                      @Param("userId") Long userId);

    /**
     * 按 {@code product_id} + {@code location_id} 原子扣减库存（WMS-MAT-001 物资领用 / 损耗）。
     *
     * <p>SQL 在 {@code WHERE} 加 {@code product_stock >= deductQty} —— MySQL 行锁 + 数量校验同步发生，
     * 并发提交（两个工人同时领同一 product+location）只有一次 affectedRows > 0。</p>
     *
     * @return affectedRows（0 = 库存不足 / product/location 不匹配 / 已软删）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = product_stock - #{deductQty},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE location_id = #{locationId} "
        + "   AND product_id  = #{productId} "
        + "   AND product_stock >= #{deductQty} "
        + "   AND del_flag = '0'")
    int deductByProductLocation(@Param("locationId") Long locationId,
                                @Param("productId") Long productId,
                                @Param("deductQty") BigDecimal deductQty,
                                @Param("userId") Long userId);

    /**
     * 按 {@code product_id} + {@code location_id} 增加库存（WMS-MAT-001 物资退回）。
     *
     * <p>退回是"加回库存"，无需校验上限；但需要保证库存记录存在（不存在不允许凭空创建库存，service 层
     * 走 update 失败兜底）。{@code update_time / update_by} 同步刷新。</p>
     *
     * @return affectedRows（0 = location_id / product_id 不匹配，service 兜底）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = product_stock + #{addQty},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE location_id = #{locationId} "
        + "   AND product_id  = #{productId} "
        + "   AND del_flag = '0'")
    int addByProductLocation(@Param("locationId") Long locationId,
                             @Param("productId") Long productId,
                             @Param("addQty") BigDecimal addQty,
                             @Param("userId") Long userId);

    /**
     * 按 {@code product_info.belong_type} 聚合活跃库存总量（DJS-FIX-ADMIN-W22-003 SummaryBar）。
     *
     * <p>JOIN {@code t_warehouse_product_info} 取 belong_type；只统计 {@code product_stock > 0} 且未软删的行。
     * 返 NULL 时调用方兜 0。租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户）。</p>
     *
     * @param belongType 字典 {@code djs_belong_type}（pork / vegetable / white_bar / dry_good / egg / gift_box）
     * @return SUM(product_stock)；可能为 null
     */
    @Select("""
        SELECT COALESCE(SUM(s.product_stock), 0)
          FROM t_warehouse_location_stock s
          JOIN t_warehouse_product_info p
            ON p.id = s.product_id
           AND p.del_flag = '0'
           AND p.tenant_id = s.tenant_id
         WHERE s.del_flag = '0'
           AND s.product_stock > 0
           AND s.tenant_id = '1001'
           AND p.belong_type = #{belongType}
        """)
    BigDecimal sumStockByBelongType(@Param("belongType") String belongType);

    /**
     * 按 {@code product_info.product_attr=2}（原材料）聚合库存总量。
     *
     * <p>口径：{@code product_attr=2}（原材料）属性的产品下所有 location_stock 求和；
     * 用于"其他"业态需求确认 SummaryBar 的"当前原料库存"显示。</p>
     */
    @Select("""
        SELECT COALESCE(SUM(s.product_stock), 0)
          FROM t_warehouse_location_stock s
          JOIN t_warehouse_product_info p
            ON p.id = s.product_id
           AND p.del_flag = '0'
           AND p.tenant_id = s.tenant_id
         WHERE s.del_flag = '0'
           AND s.product_stock > 0
           AND s.tenant_id = '1001'
           AND p.product_attr = 2
        """)
    BigDecimal sumRawMaterialStock();

    /**
     * mp 包材库列表（WMS-FLOW-001）：以 {@code product_info.belong_type=#{belongType}} 产品为粒度，
     * LEFT JOIN location_stock 聚合当前库存 + 最近盘点日期。
     *
     * <p>口径：belong_type 在后端强制 eq（不在前端 filter，契约 14）。一个包材产品可分布多个库位
     * → {@code SUM(product_stock)} / {@code MAX(latest_check_time)}。产品无任何 location_stock 行
     * → currentStock=0 / latestCheckTime=null（LEFT JOIN 保留产品行）。租户单租户显式 {@code tenant_id='1001'}。</p>
     *
     * <p>排序：{@code sortBy='stock'} → 库存升序（缺货优先）；否则按产品名（拼音/字典序）。</p>
     *
     * @param belongType 字典 {@code djs_belong_type}（包材为 {@code package}）
     * @param sortBy     {@code stock} 库存升序 / 其余按 product_name
     * @return 包材列表项
     */
    @Select("""
        <script>
        SELECT p.id                          AS productId,
               p.product_id                  AS productCode,
               p.product_name                AS productName,
               p.product_unit                AS productUnit,
               COALESCE(SUM(s.product_stock), 0) AS currentStock,
               MAX(s.latest_check_time)      AS latestCheckTime
          FROM t_warehouse_product_info p
          LEFT JOIN t_warehouse_location_stock s
            ON s.product_id = p.id
           AND s.del_flag   = '0'
           AND s.tenant_id  = p.tenant_id
         WHERE p.del_flag    = '0'
           AND p.tenant_id   = '1001'
           AND p.belong_type = #{belongType}
         GROUP BY p.id, p.product_id, p.product_name, p.product_unit
        <choose>
          <when test="sortBy == 'stock'">
            ORDER BY currentStock ASC, p.product_name ASC
          </when>
          <otherwise>
            ORDER BY p.product_name ASC
          </otherwise>
        </choose>
        </script>
        """)
    List<PackingItemVo> selectPackingItems(@Param("belongType") String belongType,
                                           @Param("sortBy") String sortBy);

    /**
     * 包材种类数（WMS-FLOW-001）：DISTINCT 未软删 {@code belong_type=#{belongType}} 产品数。
     */
    @Select("""
        SELECT COUNT(1)
          FROM t_warehouse_product_info p
         WHERE p.del_flag    = '0'
           AND p.tenant_id   = '1001'
           AND p.belong_type = #{belongType}
        """)
    Long countProductsByBelongType(@Param("belongType") String belongType);

    /**
     * 指定归属类型产品的最近一次盘点时间（WMS-FLOW-001 包材库今日总览 KPI）。
     *
     * <p>MAX(latest_check_time) over 该 belong_type 下所有 location_stock；无盘点返 null。</p>
     */
    @Select("""
        SELECT MAX(s.latest_check_time)
          FROM t_warehouse_location_stock s
          JOIN t_warehouse_product_info p
            ON p.id = s.product_id
           AND p.del_flag = '0'
           AND p.tenant_id = s.tenant_id
         WHERE s.del_flag    = '0'
           AND s.tenant_id   = '1001'
           AND p.belong_type = #{belongType}
        """)
    Date selectLatestCheckTimeByBelongType(@Param("belongType") String belongType);

    /**
     * 盘点完成回写（WMS-STOCK-001 completeCheck）：按 {@code product_id} + {@code location_id}
     * 把库存设为实盘绝对值，并刷新 {@code latest_check_time} / {@code check_result}。
     *
     * <p>与领用 / 退回的"增量"语义不同——盘点是"以实盘量校准账面"，直接 SET 绝对值。
     * {@code tenant_id} 由 MP 多租户拦截器在 final SQL 阶段注入；不走 MetaObjectHandler.updateFill，
     * 手工 set {@code update_by} / {@code update_time}。</p>
     *
     * @param locationId  库位 ID
     * @param productId   产品 ID
     * @param checkStock  实盘量（设为新库存绝对值）
     * @param checkResult 盘点结果（字典 {@code djs_check_result}）
     * @param userId      操作人
     * @return affectedRows（0 = location/product 不匹配，service 兜底处理）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = #{checkStock},"
        + "       latest_check_time = NOW(),"
        + "       check_result = #{checkResult},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE location_id = #{locationId} "
        + "   AND product_id  = #{productId} "
        + "   AND del_flag = '0'")
    int setStockAfterCheck(@Param("locationId") Long locationId,
                           @Param("productId") Long productId,
                           @Param("checkStock") BigDecimal checkStock,
                           @Param("checkResult") Integer checkResult,
                           @Param("userId") Long userId);

}
