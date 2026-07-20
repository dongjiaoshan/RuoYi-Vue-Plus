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
     * 消费「近 3 天已领用药品」清单（id 来自 {@link #listRecentPickedMedicineIds}）。
     *
     * @param ids 药品商品 id 集合
     * @return 药品商品行；无则空 list（入参空亦返空 list）
     */
    List<MedicineProductDto> listMedicineProductsByIds(Collection<Long> ids);

    /**
     * 列出某操作人「近 3 天已领用」的 distinct 药品商品 id（{@code buy_class='medicine'}），
     * 供用药治疗「使用药品」picker 数据源，按最近领用时间倒序。
     *
     * <p>数据源统一为仓库领用出库流水 {@code t_warehouse_stock_flow}（flow_type=dept_pick_out，
     * 近 3 天），覆盖<b>两个药品领用入口</b>（疫苗药品页药品领用 + 物资领用药品库领用）——两入口经本 provider /
     * MatFlow 领用都落 dept_pick_out 流水，故从流水取「已领药品」天然一致（row131：从物资领用药品库领用
     * 出来的药品也能在「使用药品」里显示）。取代原「只从 {@code t_breed_medicine_usage} 取」的口径
     * （那只覆盖疫苗药品页入口）。</p>
     *
     * @param operatorId 操作人 user_id（null = 不限，全场近 3 天已领药品，admin 端）
     * @return 药品商品 id 列表（按最近领用时间倒序，DISTINCT）；无则空 list
     */
    List<Long> listRecentPickedMedicineIds(Long operatorId);

    /**
     * 扣减药品库存（领用 use），落该药品商品库存所在库位（{@code selectDefaultLocationByProduct}）。
     * 实现方同事务落一条领用出库流水（{@code t_warehouse_stock_flow}，flow_type=dept_pick_out）。
     * 库存不足抛业务异常（不吞）。
     *
     * @param productId  药品商品 id（{@code t_warehouse_product_info.id}）
     * @param qty        扣减数量（>0）
     * @param operatorId 操作人 userId
     */
    void deduct(Long productId, BigDecimal qty, Long operatorId);

    /**
     * 扣减药品库存（损耗 loss）：扣减语义同 {@link #deduct}，但实现方按损耗落流水
     * （flow_type=loss）并同步双写仓库统一损耗台账 {@code t_warehouse_loss_flow}
     * （损耗总览 / 库存详情损耗 tab 数据源）。库存不足抛业务异常（不吞）。
     *
     * @param productId  药品商品 id
     * @param qty        损耗数量（>0）
     * @param operatorId 操作人 userId
     */
    void deductLoss(Long productId, BigDecimal qty, Long operatorId);

    /**
     * 增加药品库存（领用退回），落该药品商品原库存所在库位。实现方同事务落一条领用退回
     * 入库流水（flow_type=pick_return_in，计入退回额度、不计入采购统计）。
     *
     * @param productId  药品商品 id
     * @param qty        增加数量（>0）
     * @param operatorId 操作人 userId
     */
    void add(Long productId, BigDecimal qty, Long operatorId);

    /**
     * 增加药品库存（批次采购入库），落该药品商品原库存所在库位。实现方同事务落一条采购
     * 入库流水（flow_type=purchase_in，计入采购入库记录页 / 产品月采购统计）。
     *
     * @param productId  药品商品 id
     * @param qty        入库数量（>0）
     * @param operatorId 操作人 userId
     */
    void addPurchase(Long productId, BigDecimal qty, Long operatorId);

    /**
     * 批量取药品当前库存合计（供领用列表回显）。
     *
     * @param productIds 药品商品 id 集合
     * @return {@code productId -> SUM(product_stock)}；无库存的药品不出 key（调用方缺省按 0 处理）
     */
    Map<Long, BigDecimal> getStocks(Collection<Long> productIds);

    /**
     * 单个药品今日三量（已领 / 退回 / 损耗），口径与药品领用列表卡完全一致（读仓库出入库流水
     * {@code t_warehouse_stock_flow}，全场、今日）。供药品领用详情页 today-stat 与列表同源，
     * 修 row7：详情原查养殖台账 {@code t_breed_medicine_usage} 只含养殖入口、漏仓库物资领用入口，
     * 导致详情三量恒 ≤ 列表、派生的最大可退回/可录入损耗偏小。
     *
     * @param medicineId 药品商品 ID（为 null 返回全 0）
     * @return {@code {"use","return","loss"} -> 数量}（缺省 0，key 顺序对齐前端 MedUsageTodayStat）
     */
    Map<String, BigDecimal> todayFlowStat(Long medicineId);

}
