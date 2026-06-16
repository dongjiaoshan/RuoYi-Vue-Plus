package org.dromara.djs.common.medicine.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * 药品库存提供方（ADR-0012 药品归仓库库位统一 · FIX-MED-MODEL-004/005）。
 *
 * <p>跨模块 facade 契约：库存真值在仓库 {@code t_warehouse_location_stock}（medicine 维），
 * 由 {@code ruoyi-djs-warehouse} 注册 Spring Bean 实现本接口；养殖 med 域（领用/退回/损耗/治疗/入库）
 * 通过本接口消费仓库库存，不直接依赖 warehouse 领域类，因此零循环依赖
 * （依赖方向 warehouse → breed → common，breed 只依赖 common 的本接口）。</p>
 *
 * <p>实现方约定：单租户 {@code '1001'} + 软删 {@code del_flag='0'}；落到 {@code location_type='medicine'}
 * 的默认药品库位；并发安全复用仓库现有原子扣减（WHERE product_stock>=qty）。</p>
 *
 * @author djs
 * @since FIX-MED-MODEL-005
 */
public interface MedicineStockProvider {

    /**
     * 扣减药品库存（领用 / 损耗 / 治疗用药），落默认药品库位。
     * 库存不足抛业务异常（不吞）。
     *
     * @param medicineId 药品主数据 id（{@code t_breed_medicine_info.id}）
     * @param qty        扣减数量（>0）
     * @param operatorId 操作人 userId
     */
    void deduct(Long medicineId, BigDecimal qty, Long operatorId);

    /**
     * 增加药品库存（退回 / 采购入库），落默认药品库位；无库存行则新建。
     *
     * @param medicineId 药品主数据 id
     * @param qty        增加数量（>0）
     * @param operatorId 操作人 userId
     */
    void add(Long medicineId, BigDecimal qty, Long operatorId);

    /**
     * 批量取药品当前库存合计（供领用列表回显）。
     *
     * @param medicineIds 药品 id 集合
     * @return {@code medicineId -> SUM(product_stock)}；无库存的药品不出 key（调用方缺省按 0 处理）
     */
    Map<Long, BigDecimal> getStocks(Collection<Long> medicineIds);

}
