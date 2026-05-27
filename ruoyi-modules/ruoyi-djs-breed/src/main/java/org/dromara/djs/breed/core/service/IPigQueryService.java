package org.dromara.djs.breed.core.service;

/**
 * 猪只领域只读查询接口（跨模块薄壳）。
 *
 * <p>暴露给非养殖域（如 {@code ruoyi-djs-warehouse} 燎毛 / 屠宰）做最小必要的只读查询，
 * 替代之前的 native query mapper 跨表读法（如 warehouse 端的 {@code PigInfoReadMapper}）。
 * 跨模块只通过本接口，不能反查 {@code PigMapper}。</p>
 *
 * <p>当前只暴露"按耳号查 current_status"一项；后续如需"按 ID 查"/"按批次查"再补，
 * 不要在 warehouse 等下游模块写新的 native query mapper。</p>
 *
 * @author djs
 */
public interface IPigQueryService {

    /**
     * 查指定耳号的猪只 {@code current_status}（未软删）。
     *
     * <p>{@code tenant_id} 由 MP 多租户拦截器在 final SQL 注入，应用层无需显式 WHERE。</p>
     *
     * @param earNo 猪只耳号
     * @return {@code current_status} 字符串（{@code END}/{@code HB}/{@code PZ} ...）；耳号不存在或已软删返 {@code null}
     */
    String selectCurrentStatusByEarNo(String earNo);

}
