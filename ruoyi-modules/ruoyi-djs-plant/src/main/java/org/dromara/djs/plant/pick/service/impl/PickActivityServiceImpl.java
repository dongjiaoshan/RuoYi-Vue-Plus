package org.dromara.djs.plant.pick.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.pick.domain.PickActivity;
import org.dromara.djs.plant.pick.domain.bo.PickActivityBo;
import org.dromara.djs.plant.pick.domain.query.PickActivityQuery;
import org.dromara.djs.plant.pick.domain.vo.PickActivityVo;
import org.dromara.djs.plant.pick.mapper.PickActivityMapper;
import org.dromara.djs.plant.pick.service.IPickActivityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 采摘活动 Service 实现（PLT-PLAN-002）。
 *
 * <p>5 CRUD + activity_no inline 编码 + 活动结束聚合回填。</p>
 *
 * <p>编码：{@code PA + yyyyMMdd + 3 位}（不高频，3 位 = 999 条/天足够）。
 * inline 实现：{@link PickActivityMapper#selectMaxSeqByPrefix} 查同前缀 MAX(seq) + 1，
 * UNIQUE(tenant_id, activity_no, del_unique) 兜底并发冲突。</p>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Slf4j
@Service
public class PickActivityServiceImpl extends DjsBaseServiceImpl<PickActivityMapper, PickActivity>
    implements IPickActivityService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final CropInfoMapper cropMapper;

    public PickActivityServiceImpl(PickActivityMapper baseMapper, CropInfoMapper cropMapper) {
        super(baseMapper);
        this.cropMapper = cropMapper;
    }

    @Override
    public TableDataInfo<PickActivityVo> queryPageList(PickActivityQuery query, PageQuery pageQuery) {
        Page<PickActivityVo> page = baseMapper.selectVoPage(pageQuery.build(), buildWrapper(query));
        return TableDataInfo.build(page);
    }

    @Override
    public List<PickActivityVo> queryList(PickActivityQuery query) {
        return baseMapper.selectVoList(buildWrapper(query));
    }

    @Override
    public PickActivityVo getInfo(Long id) {
        PickActivityVo vo = baseMapper.selectVoById(id);
        if (vo == null) {
            throw new ServiceException("采摘活动不存在或已删除：" + id);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createByBo(PickActivityBo bo) {
        validateBo(bo, true);
        PickActivity entity = new PickActivity();
        entity.setActivityNo(nextActivityNo(bo.getActivityDate()));
        entity.setActivityName(bo.getActivityName());
        entity.setActivityDate(bo.getActivityDate());
        entity.setActivityStatus(StringUtils.isBlank(bo.getActivityStatus()) ? "upcoming" : bo.getActivityStatus());
        entity.setCropId(bo.getCropId());
        entity.setCropName(resolveCropName(bo.getCropId()));
        entity.setTotalPlot(bo.getTotalPlot() == null ? 0 : bo.getTotalPlot());
        entity.setTotalYield(bo.getTotalYield() == null ? BigDecimal.ZERO : bo.getTotalYield());
        entity.setVisitorCount(bo.getVisitorCount());
        entity.setRemark(bo.getRemark());
        baseMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PickActivityBo bo) {
        if (bo == null || bo.getId() == null) {
            throw new ServiceException("活动 id 必填");
        }
        validateBo(bo, false);
        PickActivity existing = baseMapper.selectById(bo.getId());
        if (existing == null) {
            throw new ServiceException("采摘活动不存在或已删除：" + bo.getId());
        }
        PickActivity patch = new PickActivity();
        patch.setId(bo.getId());
        // activity_no 不允许改（业务码）
        if (StringUtils.isNotBlank(bo.getActivityName())) {
            patch.setActivityName(bo.getActivityName());
        }
        if (bo.getActivityDate() != null) {
            patch.setActivityDate(bo.getActivityDate());
        }
        if (StringUtils.isNotBlank(bo.getActivityStatus())) {
            patch.setActivityStatus(bo.getActivityStatus());
        }
        if (bo.getCropId() != null) {
            patch.setCropId(bo.getCropId());
            if (!bo.getCropId().equals(existing.getCropId())) {
                patch.setCropName(resolveCropName(bo.getCropId()));
            }
        }
        if (bo.getTotalPlot() != null) {
            patch.setTotalPlot(bo.getTotalPlot());
        }
        if (bo.getTotalYield() != null) {
            patch.setTotalYield(bo.getTotalYield());
        }
        if (bo.getVisitorCount() != null) {
            patch.setVisitorCount(bo.getVisitorCount());
        }
        if (bo.getRemark() != null) {
            patch.setRemark(bo.getRemark());
        }
        return baseMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return 0;
        }
        return softDelete(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PickActivityVo summary(Long id) {
        PickActivity existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new ServiceException("采摘活动不存在或已删除：" + id);
        }
        BigDecimal yield = baseMapper.sumYieldForActivity(existing.getCropId(), existing.getActivityDate());
        existing.setTotalYield(yield != null ? yield : BigDecimal.ZERO);
        existing.setActivityStatus("ended");
        baseMapper.updateById(existing);
        return baseMapper.selectVoById(id);
    }

    // ============================================================
    // helpers
    // ============================================================
    private void validateBo(PickActivityBo bo, boolean isCreate) {
        if (bo == null) {
            throw new ServiceException("入参为空");
        }
        if (bo.getActivityDate() == null) {
            throw new ServiceException("活动日期必填");
        }
        if (bo.getCropId() == null) {
            throw new ServiceException("作物必选");
        }
        if (StringUtils.isNotBlank(bo.getActivityStatus())) {
            Set<String> allowed = Set.of("upcoming", "ongoing", "ended");
            if (!allowed.contains(bo.getActivityStatus())) {
                throw new ServiceException("非法 activity_status：" + bo.getActivityStatus());
            }
        }
    }

    private String resolveCropName(Long cropId) {
        CropInfo crop = cropMapper.selectById(cropId);
        if (crop == null) {
            throw new ServiceException("作物不存在：" + cropId);
        }
        return crop.getCropName();
    }

    private LambdaQueryWrapper<PickActivity> buildWrapper(PickActivityQuery q) {
        LambdaQueryWrapper<PickActivity> w = new LambdaQueryWrapper<>();
        if (q == null) {
            return w.orderByDesc(PickActivity::getActivityDate).orderByDesc(PickActivity::getId);
        }
        w.like(StringUtils.isNotBlank(q.getActivityNo()), PickActivity::getActivityNo, q.getActivityNo())
            .like(StringUtils.isNotBlank(q.getActivityName()), PickActivity::getActivityName, q.getActivityName())
            .eq(StringUtils.isNotBlank(q.getActivityStatus()), PickActivity::getActivityStatus, q.getActivityStatus())
            .eq(q.getCropId() != null, PickActivity::getCropId, q.getCropId())
            .ge(q.getDateFrom() != null, PickActivity::getActivityDate, q.getDateFrom())
            .le(q.getDateTo() != null, PickActivity::getActivityDate, q.getDateTo())
            .orderByDesc(PickActivity::getActivityDate)
            .orderByDesc(PickActivity::getId);
        return w;
    }

    /**
     * 生成 activity_no = {@code PA + yyyyMMdd + 3 位}。
     */
    String nextActivityNo(LocalDate activityDate) {
        String prefix = "PA" + DATE_FMT.format(activityDate);
        String tenantId = currentTenantSafe();
        Integer max = baseMapper.selectMaxSeqByPrefix(tenantId, prefix);
        int next = (max == null ? 0 : max) + 1;
        if (next > 999) {
            throw new ServiceException("当日采摘活动序号已达上限 999，请人工介入");
        }
        return prefix + String.format("%03d", next);
    }

    private String currentTenantSafe() {
        try {
            String t = LoginHelper.getTenantId();
            return (t != null && !t.isBlank()) ? t : "1001";
        } catch (Exception e) {
            return "1001";
        }
    }

    @SuppressWarnings("unused")
    private List<PickActivity> noop() {
        return Collections.emptyList();
    }
}
