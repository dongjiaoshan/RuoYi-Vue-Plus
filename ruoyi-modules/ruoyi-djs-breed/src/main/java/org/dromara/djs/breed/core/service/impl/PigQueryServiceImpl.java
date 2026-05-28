package org.dromara.djs.breed.core.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IPigQueryService;
import org.springframework.stereotype.Service;

/**
 * {@link IPigQueryService} 默认实现。
 *
 * <p>薄薄一层 mapper delegate，给跨域只读使用（如 warehouse 燎毛 / 屠宰 / 需求指定）。
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

    @Override
    public TableDataInfo<PigAvailableVo> listAvailableForOutbound(PageQuery pageQuery) {
        PageQuery pq = pageQuery != null ? pageQuery : new PageQuery(1, 10);
        IPage<PigAvailableVo> page = pigMapper.selectAvailableForOutboundPage(pq.build());
        return TableDataInfo.build(page);
    }

    @Override
    public int countAvailableForOutbound() {
        return pigMapper.countAvailableForOutbound();
    }
}
