package org.dromara.djs.common.supplier.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 供应商交易明细数据提供方（DJS-FIX-ADMIN-W22-005）。
 *
 * <p>跨模块 facade 契约：breed（药品入库）/ warehouse（物资入库）各注册一个 Spring Bean
 * 实现本接口，common 的 SupplierController 通过 {@code ObjectProvider<SupplierDealProvider>}
 * 收集所有实现并 UNION，不直接依赖上游模块的领域类，因此零循环依赖。</p>
 *
 * <p>实现方约定 read-only：只 SELECT、不分页（聚合在 controller 统一做）、单租户
 * {@code '1001'} + 软删 {@code del_flag='0'} 过滤；无数据返空 list 不返 null。</p>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-005
 */
public interface SupplierDealProvider {

    /**
     * 按供应商 ID 聚合该来源下的全部交易明细（不分页）。
     *
     * @param supplierId 供应商主键（{@code t_md_supplier.id}）
     * @return 交易明细行；无数据返空 list（不返 null）
     */
    List<SupplierDealVo> aggregateBySupplier(Long supplierId);

    /**
     * 批量聚合多个供应商的交易统计（次数 + 购入量），由 DB 一条 {@code GROUP BY} 直出，避免列表页 N+1。
     *
     * <p>供应商列表页（{@code /djs/common/supplier/list}）回填 {@code dealCount} / {@code purchaseQty} 用：
     * 取本页全部供应商 ID 一次性传入，每个实现方只发 1 条聚合查询，把原来「N 供应商 × M 来源」条捞全明细的慢查询
     * 压成「M 来源 × 1」条。</p>
     *
     * <p>本 default 实现为兜底：回退到逐个 {@link #aggregateBySupplier(Long)} 在内存数行 + 求和，保证未来未知实现方
     * 不重写本方法也零破坏。生产实现方（药品入库 / 物资入库）应 override 走批量 mapper。</p>
     *
     * @param ids 供应商 ID 集合（null / 空集合返空 Map，不查 DB）
     * @return {@code supplierId → 统计}；无交易的 ID 不在结果中（调用方用 {@link SupplierDealStat#ZERO} 兜底）
     */
    default Map<Long, SupplierDealStat> aggregateStatsBySupplierIds(Collection<Long> ids) {
        Map<Long, SupplierDealStat> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            List<SupplierDealVo> deals = aggregateBySupplier(id);
            if (deals == null || deals.isEmpty()) {
                continue;
            }
            BigDecimal sum = deals.stream()
                .map(SupplierDealVo::getDealQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.merge(id, new SupplierDealStat(deals.size(), sum), SupplierDealStat::plus);
        }
        return result;
    }

}
