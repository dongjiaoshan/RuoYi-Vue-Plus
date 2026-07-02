package org.dromara.djs.warehouse.product.service;

import java.util.Collection;
import java.util.Map;

/**
 * 产品展示名解析器（DENGBO-R16）。
 *
 * <p>果蔬（{@code belong_type='vegetable'}）产品按「原材料对应作物是否有<b>有效有机证书</b>」决定展示名：
 * 有证 → 产品名；无证 → 产品别名（别名空回落产品名）。非果蔬产品一律产品名。</p>
 *
 * <p>供以下写入 / 展示落点统一调用：门店需求下单 / 需求管理（写入时定格 {@code demand_manage.product_name}）、
 * 果蔬打包间 / 产品生产（写入时定格 {@code product_production.product_name}）、追溯码生码（定格
 * {@code trace_code.trace_display_name}）。列表场景用批量版避免 N+1。</p>
 *
 * @author djs
 * @since DENGBO-R16
 */
public interface IProductDisplayNameResolver {

    /**
     * 单个产品展示名。查不到 / 异常一律兜底返回 {@code fallbackName}（通常传产品当前名，绝不返回空）。
     *
     * @param productId    产品主键
     * @param fallbackName 兜底名（productId 为空 / 产品不存在 / 查询异常时返回）
     * @return 展示名（永不为空，最坏返回 fallbackName）
     */
    String resolveDisplayName(Long productId, String fallbackName);

    /**
     * 批量解析展示名（列表 enrich 防 N+1）。返回 {@code Map<productId, displayName>}；
     * 未命中的 productId 不在结果中（调用方对缺失自行兜底）。异常时返回空 Map（调用方兜底）。
     *
     * @param productIds 产品主键集合（可空 / 空集合 → 返回空 Map）
     * @return productId → 展示名
     */
    Map<Long, String> resolveDisplayNames(Collection<Long> productIds);

}
