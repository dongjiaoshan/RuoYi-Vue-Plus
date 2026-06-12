package org.dromara.djs.plant.pick.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.plant.pick.domain.query.PickActivityQuery;
import org.dromara.djs.plant.pick.domain.vo.PickActivityVo;
import org.dromara.djs.plant.pick.mapper.PickActivityMapper;
import org.dromara.djs.plant.pick.service.IPickActivityService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 采摘活动只读聚合报表 Service 实现。
 *
 * <p>对 {@code t_plant_plant_details} 按「作物 + 活动日期」聚合（{@link PickActivityMapper#selectDailyReport}）。
 * 数据量小，分页在应用层切（与本模块 mp 端「应用层聚合」一致）。</p>
 *
 * @author djs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PickActivityServiceImpl implements IPickActivityService {

    private final PickActivityMapper activityMapper;

    @Override
    public TableDataInfo<PickActivityVo> queryPageList(PickActivityQuery query, PageQuery pageQuery) {
        List<PickActivityVo> all = queryList(query);
        long total = all.size();

        int pageNum = pageQuery == null || pageQuery.getPageNum() == null ? 1 : Math.max(1, pageQuery.getPageNum());
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null ? 10 : Math.max(1, pageQuery.getPageSize());
        int from = Math.min((pageNum - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());

        return new TableDataInfo<>(all.subList(from, to), total);
    }

    @Override
    public List<PickActivityVo> queryList(PickActivityQuery query) {
        PickActivityQuery q = query == null ? new PickActivityQuery() : query;
        return activityMapper.selectDailyReport(currentTenantSafe(), q.getCropId(), q.getActivityDate());
    }

    private String currentTenantSafe() {
        try {
            String t = LoginHelper.getTenantId();
            return (t != null && !t.isBlank()) ? t : "1001";
        } catch (Exception e) {
            return "1001";
        }
    }
}
