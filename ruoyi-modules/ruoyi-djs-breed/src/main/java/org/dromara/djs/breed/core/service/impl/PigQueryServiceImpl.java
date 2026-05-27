package org.dromara.djs.breed.core.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IPigQueryService;
import org.springframework.stereotype.Service;

/**
 * {@link IPigQueryService} 默认实现。
 *
 * <p>薄薄一层 mapper delegate，给跨域只读使用（如 warehouse 燎毛 / 屠宰）。
 * 业务逻辑不进这里，需要更复杂的查询时另开 service。</p>
 *
 * @author djs
 */
@Service
@RequiredArgsConstructor
public class PigQueryServiceImpl implements IPigQueryService {

    private final PigMapper pigMapper;

    @Override
    public String selectCurrentStatusByEarNo(String earNo) {
        if (earNo == null || earNo.isBlank()) {
            return null;
        }
        return pigMapper.selectCurrentStatusByEarNo(earNo);
    }
}
