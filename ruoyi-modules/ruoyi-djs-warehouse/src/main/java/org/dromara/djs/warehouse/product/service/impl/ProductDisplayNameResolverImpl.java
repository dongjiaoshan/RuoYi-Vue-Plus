package org.dromara.djs.warehouse.product.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.warehouse.product.mapper.VegDisplayNameMapper;
import org.dromara.djs.warehouse.product.service.IProductDisplayNameResolver;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 产品展示名解析器实现（DENGBO-R16）。
 *
 * <p>果蔬按证 / 别名、非果蔬直接产品名的判定全部下推到 {@link VegDisplayNameMapper} 的跨表 SQL
 * （单条 EXISTS 直算，批量一次 IN 查询，均免 N+1）。任何查询异常一律兜底（返回 fallback / 空 Map），
 * 展示名解析绝不拖垮主业务（下单 / 打包 / 生码）。</p>
 *
 * @author djs
 * @since DENGBO-R16
 */
@Slf4j
@Service
public class ProductDisplayNameResolverImpl implements IProductDisplayNameResolver {

    private final VegDisplayNameMapper vegDisplayNameMapper;

    public ProductDisplayNameResolverImpl(VegDisplayNameMapper vegDisplayNameMapper) {
        this.vegDisplayNameMapper = vegDisplayNameMapper;
    }

    @Override
    public String resolveDisplayName(Long productId, String fallbackName) {
        if (productId == null) {
            return fallbackName;
        }
        try {
            String name = vegDisplayNameMapper.resolveDisplayName(productId);
            return StringUtils.isNotBlank(name) ? name : fallbackName;
        } catch (Exception e) {
            log.warn("[DENGBO-R16] resolveDisplayName failed (fallback) productId={}", productId, e);
            return fallbackName;
        }
    }

    @Override
    public Map<Long, String> resolveDisplayNames(Collection<Long> productIds) {
        Map<Long, String> result = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return result;
        }
        // 去重 + 去空，避免 IN 里塞 null
        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return result;
        }
        try {
            List<Map<String, Object>> rows = vegDisplayNameMapper.resolveDisplayNameBatch(ids);
            for (Map<String, Object> row : rows) {
                Object idObj = row.get("productId");
                Object nameObj = row.get("displayName");
                if (idObj == null || nameObj == null) {
                    continue;
                }
                Long id = ((Number) idObj).longValue();
                String name = nameObj.toString();
                if (StringUtils.isNotBlank(name)) {
                    result.put(id, name);
                }
            }
        } catch (Exception e) {
            log.warn("[DENGBO-R16] resolveDisplayNames failed (empty) size={}: {}",
                ids.size(), e.getMessage());
        }
        return result;
    }

}
