package org.dromara.djs.common.supplier.api;

import java.util.List;

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

}
