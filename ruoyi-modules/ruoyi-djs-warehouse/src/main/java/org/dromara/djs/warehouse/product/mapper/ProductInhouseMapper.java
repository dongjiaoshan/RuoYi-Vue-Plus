package org.dromara.djs.warehouse.product.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;

/**
 * 过程产品 Mapper（doc/11 §2.7 / WMS-PIG-002 + WMS-VEG-001 共用）。
 *
 * <p>D9 closing Group B 已从 {@code cut/mapper} 挪到 {@code product/mapper}（与 ProductInfoMapper 同域）。</p>
 *
 * <p>当前仅 INSERT；查询走 BaseMapperPlus 默认方法（admin 列表展示用）。</p>
 *
 * @author djs
 * @since WMS-PIG-002
 */
public interface ProductInhouseMapper extends BaseMapperPlus<ProductInhouse, ProductInhouse> {
}
