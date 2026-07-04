package org.dromara.djs.common.medicine.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 药品库存提供方（药品归仓库统一 · 药品即仓库商品 {@code buy_class='medicine'}）。
 *
 * <p>跨模块 facade 契约：药品作为仓库商品统一管理，主数据在 {@code t_warehouse_product_info}
 * （{@code buy_class='medicine'}）、库存真值在 {@code t_warehouse_location_stock}（product 维），
 * 由 {@code ruoyi-djs-warehouse} 注册 Spring Bean 实现本接口；养殖 med 域（领用/退回/损耗）
 * 通过本接口消费仓库药品清单 + 库存，不直接依赖 warehouse 领域类，零循环依赖
 * （依赖方向 warehouse → common，养殖 breed → common 只依赖本接口）。</p>
 *
 * <p>不再区分「养殖药品」独立目录（{@code t_breed_medicine_info} 已弃用）；本接口所有
 * {@code productId} 参数即 {@code t_warehouse_product_info.id}。实现方约定：单租户 {@code '1001'}
 * + 软删 {@code del_flag='0'}；扣减/退回落到该药品商品库存所在库位（{@code selectDefaultLocationByProduct}
 * 取库存最多的库位），并发安全复用仓库原子扣减（WHERE product_stock>=qty）。</p>
 *
 * @author djs
 */
public interface MedicineStockProvider {

    /**
     * 列出全部药品商品（{@code buy_class='medicine'}）+ 当前库存合计，供养殖端「药品领用」列表消费。
     *
     * @param keyword 药品名模糊过滤（可空）
     * @return 药品商品行（按药品名排序）；无则空 list
     */
    List<MedicineProductDto> listMedicineProducts(String keyword);

    /**
     * 按 id 集合列出药品商品（{@code buy_class='medicine'}）+ 当前库存合计，供「用药治疗 / 批量用药」
     * 消费「近 3 天已领用药品」清单（id 来自 {@code t_breed_medicine_usage} 领用台账）。
     *
     * @param ids 药品商品 id 集合
     * @return 药品商品行；无则空 list（入参空亦返空 list）
     */
    List<MedicineProductDto> listMedicineProductsByIds(Collection<Long> ids);

    /**
     * 扣减药品库存（领用 / 损耗），落该药品商品库存所在库位（{@code selectDefaultLocationByProduct}）。
     * 库存不足抛业务异常（不吞）。
     *
     * @param productId  药品商品 id（{@code t_warehouse_product_info.id}）
     * @param qty        扣减数量（>0）
     * @param operatorId 操作人 userId
     */
    void deduct(Long productId, BigDecimal qty, Long operatorId);

    /**
     * 增加药品库存（退回），落该药品商品原库存所在库位。
     *
     * @param productId  药品商品 id
     * @param qty        增加数量（>0）
     * @param operatorId 操作人 userId
     */
    void add(Long productId, BigDecimal qty, Long operatorId);

    /**
     * 批量取药品当前库存合计（供领用列表回显）。
     *
     * @param productIds 药品商品 id 集合
     * @return {@code productId -> SUM(product_stock)}；无库存的药品不出 key（调用方缺省按 0 处理）
     */
    Map<Long, BigDecimal> getStocks(Collection<Long> productIds);

}
