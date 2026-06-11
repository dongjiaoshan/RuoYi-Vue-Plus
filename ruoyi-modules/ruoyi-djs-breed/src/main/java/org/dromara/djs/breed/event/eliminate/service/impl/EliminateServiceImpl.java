package org.dromara.djs.breed.event.eliminate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.satoken.utils.LoginHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.I18nMessages;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.eliminate.domain.PigCulling;
import org.dromara.djs.breed.event.eliminate.domain.bo.EliminateBo;
import org.dromara.djs.breed.event.eliminate.domain.query.EliminateQuery;
import org.dromara.djs.breed.event.eliminate.domain.vo.PigCullingVo;
import org.dromara.djs.breed.event.eliminate.mapper.PigCullingMapper;
import org.dromara.djs.breed.event.eliminate.service.IEliminateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 淘汰事件 Service 实现（BRD-EVENT-004 ELIMINATE）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EliminateServiceImpl implements IEliminateService {

    private final PigCullingMapper cullingMapper;
    private final PigMapper pigMapper;
    private final IPigCoreService pigCoreService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigCullingVo recordEliminate(EliminateBo bo) {
        Objects.requireNonNull(bo, "EliminateBo must not be null");

        // 二选一支持：mp 端传 earNo；admin 端传 pigId（与 D5 BRD-EVENT-001 supplierCode 模式一致）
        if (bo.getPigId() == null) {
            if (bo.getEarNo() == null || bo.getEarNo().isBlank()) {
                throw new ServiceException(I18nMessages.t("pig.id_or_ear_required"), 400);
            }
            Long resolved = pigMapper.selectIdByEarNo(bo.getEarNo());
            if (resolved == null) {
                throw new ServiceException(I18nMessages.t("pig.not_found_by_ear", bo.getEarNo()), 400);
            }
            bo.setPigId(resolved);
        }
        Pig pig = pigMapper.selectById(bo.getPigId());
        if (pig == null) {
            throw new ServiceException(I18nMessages.t("pig.not_found", bo.getPigId()));
        }

        if (StringUtils.isBlank(bo.getOssIds())) {
            throw new ServiceException(I18nMessages.t("eliminate.photo.required"));
        }

        PigCulling entity = new PigCulling();
        entity.setPigId(pig.getId());
        entity.setEarNo(pig.getEarNo());
        entity.setCullingDate(bo.getCullingDate());
        entity.setCullingReason(bo.getCullingReason());
        entity.setCullingDest(bo.getCullingDest());
        entity.setCullingWeight(bo.getCullingWeight());
        entity.setOssIds(bo.getOssIds());
        entity.setRemark(bo.getRemark());
        entity.setOperatorId(LoginHelper.getUserId());
        entity.setDelFlag("0");
        cullingMapper.insert(entity);

        PigEventBo eventBo = new PigEventBo();
        eventBo.setPigId(pig.getId());
        eventBo.setEventType(PigStatusEvent.ELIMINATE);
        eventBo.setRelatedEventId(entity.getId());
        eventBo.setEventAt(bo.getCullingDate());
        pigCoreService.fireEvent(eventBo);

        log.info("[BRD-EVENT-004] recordEliminate pigId={} earNo={} cullingId={} reason={}",
            pig.getId(), pig.getEarNo(), entity.getId(), bo.getCullingReason());

        return toVo(entity);
    }

    @Override
    public TableDataInfo<PigCullingVo> queryPage(EliminateQuery query, PageQuery pageQuery) {
        LocalDateTime beginAt = query.getBeginDate() != null ? query.getBeginDate().atStartOfDay() : null;
        LocalDateTime endBefore = query.getEndDate() != null ? query.getEndDate().plusDays(1).atStartOfDay() : null;
        LambdaQueryWrapper<PigCulling> w = Wrappers.<PigCulling>lambdaQuery()
            .eq(query.getPigId() != null, PigCulling::getPigId, query.getPigId())
            .like(StringUtils.isNotBlank(query.getEarNo()), PigCulling::getEarNo, query.getEarNo())
            .eq(StringUtils.isNotBlank(query.getCullingReason()), PigCulling::getCullingReason, query.getCullingReason())
            .ge(beginAt != null, PigCulling::getCullingDate, beginAt)
            .lt(endBefore != null, PigCulling::getCullingDate, endBefore)
            .orderByDesc(PigCulling::getCullingDate, PigCulling::getId);
        Page<PigCullingVo> page = cullingMapper.selectVoPage(pageQuery.build(), w);
        return TableDataInfo.build(page);
    }

    private PigCullingVo toVo(PigCulling e) {
        PigCullingVo v = new PigCullingVo();
        v.setId(e.getId());
        v.setPigId(e.getPigId());
        v.setEarNo(e.getEarNo());
        v.setCullingDate(e.getCullingDate());
        v.setCullingReason(e.getCullingReason());
        v.setCullingDest(e.getCullingDest());
        v.setCullingWeight(e.getCullingWeight());
        v.setOssIds(e.getOssIds());
        v.setOperatorId(e.getOperatorId());
        v.setRemark(e.getRemark());
        return v;
    }
}
