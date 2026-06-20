package org.dromara.djs.warehouse.product.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
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
     * 打包按实重部分扣减来源 inhouse 的待打包重量（行锁原子）：
     * {@code product_weight >= qty} 才扣，affected=0 表示余量不足/并发抢占（调用方回滚）。
     *
     * <p>配合 {@code consumeInhouse} 的部分消耗模型：一行领用 WIP 可分多次打包，
     * 扣到 ≤0 由调用方软删；让打包卡「原材料库存 = Σ 活动 inhouse」随打包正确递减。</p>
     *
     * @param id  来源 inhouse 主键
     * @param qty 本次打包实重（kg）
     * @return 影响行数（1=成功，0=余量不足/已被占用）
     */
    @Update("UPDATE t_warehouse_product_inhouse SET product_weight = product_weight - #{qty} "
        + "WHERE id = #{id} AND del_flag = '0' AND product_weight >= #{qty}")
    int deductWeightById(@Param("id") Long id, @Param("qty") BigDecimal qty);
}
