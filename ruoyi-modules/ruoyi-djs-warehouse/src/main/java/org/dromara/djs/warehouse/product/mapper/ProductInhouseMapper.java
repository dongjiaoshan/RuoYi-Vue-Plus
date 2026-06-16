package org.dromara.djs.warehouse.product.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;

import java.math.BigDecimal;

/**
 * 过程产品 Mapper（doc/11 §2.7 / WMS-PIG-002 + WMS-VEG-001 共用）。
 *
 * <p>D9 closing Group B 已从 {@code cut/mapper} 挪到 {@code product/mapper}（与 ProductInfoMapper 同域）。</p>
 *
 * @author djs
 * @since WMS-PIG-002
 */
public interface ProductInhouseMapper extends BaseMapperPlus<ProductInhouse, ProductInhouse> {

    /**
     * 统计某白条已分割入库的产品重量合计（白条剩余可分割重量 = pickup_weight − 此合计）。
     *
     * <p>分割出库称重时每件产品 INSERT 一行 product_inhouse（white_bar_id 关联白条），
     * 合计即为该白条已分出的产品总重。无记录返 0。</p>
     */
    @Select("SELECT COALESCE(SUM(product_weight), 0) FROM t_warehouse_product_inhouse "
        + "WHERE white_bar_id = #{whiteBarId} AND del_flag = '0'")
    BigDecimal sumProductWeightByWhiteBarId(@Param("whiteBarId") Long whiteBarId);
}
