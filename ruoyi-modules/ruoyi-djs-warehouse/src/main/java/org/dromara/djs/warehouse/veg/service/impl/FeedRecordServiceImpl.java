package org.dromara.djs.warehouse.veg.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.veg.domain.bo.FeedRecordQuery;
import org.dromara.djs.warehouse.veg.domain.vo.FeedRecordVo;
import org.dromara.djs.warehouse.veg.mapper.FeedLogMapper;
import org.dromara.djs.warehouse.veg.service.IFeedRecordService;
import org.springframework.stereotype.Service;

/**
 * 有机饲喂记录 Service 实现（WMS-FEED-RECORD-001，仓库-admin 行21）。
 *
 * @author djs
 * @since WMS-FEED-RECORD-001
 */
@Service
@RequiredArgsConstructor
public class FeedRecordServiceImpl implements IFeedRecordService {

    private final FeedLogMapper feedLogMapper;

    @Override
    public TableDataInfo<FeedRecordVo> queryPage(FeedRecordQuery query, PageQuery pageQuery) {
        // query 可能为 null（无任何筛选项时），统一兜底空对象，避免 mapper <if> NPE
        FeedRecordQuery q = query != null ? query : new FeedRecordQuery();
        // V1 固定租户 '1001'；自定义 JOIN @Select 不走拦截器自动注入，显式取当前租户传入 WHERE。
        String tenantId = TenantHelper.getTenantId();
        IPage<FeedRecordVo> page = feedLogMapper.selectRecordPage(pageQuery.build(), tenantId, q);
        return TableDataInfo.build(page);
    }
}
