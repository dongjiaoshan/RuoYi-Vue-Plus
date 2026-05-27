package org.dromara.djs.warehouse.stock.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.domain.vo.LocationStockVo;

import java.math.BigDecimal;

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

}
