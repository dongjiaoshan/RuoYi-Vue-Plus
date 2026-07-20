package org.dromara.djs.warehouse.veg.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.veg.domain.vo.FeedDailyStatVo;
import org.dromara.djs.warehouse.veg.domain.vo.FeedLogVo;
import org.dromara.djs.warehouse.veg.mapper.FeedLogMapper;
import org.dromara.djs.warehouse.veg.service.IFeedLogService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 饲料饲喂台账 Service 实现（WMS-VEG-FEED-LOG-001）。
 *
 * @author djs
 * @since WMS-VEG-FEED-LOG-001
 */
@Service
@RequiredArgsConstructor
public class FeedLogServiceImpl implements IFeedLogService {

    private final FeedLogMapper feedLogMapper;

    @Override
    public List<FeedDailyStatVo> dailyStat(Date startDate, Date endDate) {
        // V1 固定租户 '1001'；自定义聚合 @Select 不走拦截器自动注入，显式取当前租户传入 WHERE。
        String tenantId = TenantHelper.getTenantId();
        return feedLogMapper.selectDailyStat(tenantId, startDate, endDate);
    }

    @Override
    public List<FeedLogVo> listByProduct(Long productId, Date dateFrom, Date dateTo) {
        if (productId == null) {
            return Collections.emptyList();
        }
        // V1 固定租户 '1001'；自定义 @Select 不走拦截器自动注入，显式取当前租户传入 WHERE。
        String tenantId = TenantHelper.getTenantId();
        return feedLogMapper.selectByProduct(tenantId, productId, dateFrom, dateTo);
    }

}
