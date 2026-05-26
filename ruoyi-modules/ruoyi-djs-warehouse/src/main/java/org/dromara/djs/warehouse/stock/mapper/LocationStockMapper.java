package org.dromara.djs.warehouse.stock.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.domain.vo.LocationStockVo;

/**
 * 库存明细 Mapper（WMS-MD-001）。
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

}
