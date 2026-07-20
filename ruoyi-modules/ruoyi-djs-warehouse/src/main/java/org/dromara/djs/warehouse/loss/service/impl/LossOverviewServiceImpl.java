package org.dromara.djs.warehouse.loss.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.loss.domain.vo.LossOverviewDailyVo;
import org.dromara.djs.warehouse.loss.domain.vo.LossOverviewDetailVo;
import org.dromara.djs.warehouse.loss.mapper.LossOverviewMapper;
import org.dromara.djs.warehouse.loss.service.ILossOverviewService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 损耗总览 Service 实现（WMS-LOSS-OVERVIEW-001，仓库-admin 行63）。
 *
 * @author djs
 * @since WMS-LOSS-OVERVIEW-001
 */
@Service
@RequiredArgsConstructor
public class LossOverviewServiceImpl implements ILossOverviewService {

    private final LossOverviewMapper lossOverviewMapper;

    @Override
    public List<LossOverviewDailyVo> dailyOverview(Date dateFrom, Date dateTo) {
        // V1 固定租户 '1001'；自定义聚合 @Select 不走拦截器自动注入，显式取当前租户传入 WHERE。
        String tenantId = TenantHelper.getTenantId();
        return lossOverviewMapper.selectDailyOverview(tenantId, dateFrom, dateTo);
    }

    @Override
    public List<LossOverviewDetailVo> dailyDetail(String date, String productName, String lossType) {
        // date 必传（详情按某日下钻）；缺省则返回空，避免误拉全表逐条
        if (StringUtils.isBlank(date)) {
            return List.of();
        }
        String tenantId = TenantHelper.getTenantId();
        return lossOverviewMapper.selectDailyDetail(tenantId, date,
            StringUtils.isBlank(productName) ? null : productName,
            StringUtils.isBlank(lossType) ? null : lossType);
    }
}
