package org.dromara.djs.breed.event.eliminate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.satoken.utils.LoginHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.OssService;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
    private final OssService ossService;

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
        entity.setCullingRecorderId(bo.getCullingRecorderId());
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
        // 列表每行解析淘汰照片 ossIds → 可访问 URL（mp <image> 无法携 token 取鉴权下载端点，故后端预解析）
        for (PigCullingVo vo : page.getRecords()) {
            vo.setImageUrls(resolveImageUrls(vo.getOssIds()));
        }
        return TableDataInfo.build(page);
    }

    /**
     * 淘汰照片 ossId（逗号分隔）→ 可访问 URL 列表（无照片返空 list）。
     * <p>mp {@code <image>} 无法携 Bearer token 取鉴权下载端点，故后端 JOIN sys_oss 预解析；
     * {@link OssService#selectUrlByIds} 返逗号拼接 URL 串，按 {@code ,} 拆成 list。</p>
     */
    private List<String> resolveImageUrls(String ossIds) {
        if (StringUtils.isBlank(ossIds)) {
            return List.of();
        }
        String urls = ossService.selectUrlByIds(ossIds);
        if (StringUtils.isBlank(urls)) {
            return List.of();
        }
        return Arrays.stream(urls.split(","))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());
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
        v.setCullingRecorderId(e.getCullingRecorderId());
        v.setRemark(e.getRemark());
        v.setImageUrls(resolveImageUrls(e.getOssIds()));
        return v;
    }
}
