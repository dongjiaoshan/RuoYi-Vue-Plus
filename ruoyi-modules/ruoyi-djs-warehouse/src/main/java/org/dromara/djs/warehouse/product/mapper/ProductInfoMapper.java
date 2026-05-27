package org.dromara.djs.warehouse.product.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.vo.ProductInfoVo;

/**
 * 产品 Mapper（WMS-MD-002）。
 *
 * @author djs
 * @since WMS-MD-002
 */
public interface ProductInfoMapper extends BaseMapperPlus<ProductInfo, ProductInfoVo> {

    /**
     * 统计指定 product 是否仍有 {@code product_stock > 0} 的库存记录。
     *
     * <p>{@link org.dromara.djs.warehouse.product.service.impl.ProductInfoServiceImpl#deleteWithValidByIds}
     * 删除前校验使用；多租户拦截器在 final SQL 阶段自动注入 {@code tenant_id} 过滤。</p>
     */
    @org.apache.ibatis.annotations.Select(
        "SELECT COUNT(1) FROM t_warehouse_location_stock "
            + "WHERE product_id = #{productId} "
            + "  AND product_stock > 0 "
            + "  AND del_flag = '0'")
    long countActiveStockByProduct(@Param("productId") Long productId);

    /**
     * 统计是否有其他 product 把指定 id 作为原材料（{@code product_material} FK）引用。
     *
     * <p>用于级联删除校验：若某产品被其他产品作为原材料引用，禁止软删（数据完整性）。</p>
     */
    @org.apache.ibatis.annotations.Select(
        "SELECT COUNT(1) FROM t_warehouse_product_info "
            + "WHERE product_material = #{productMaterialId} "
            + "  AND del_flag = '0'")
    long countReferencedAsMaterial(@Param("productMaterialId") Long productMaterialId);

}
