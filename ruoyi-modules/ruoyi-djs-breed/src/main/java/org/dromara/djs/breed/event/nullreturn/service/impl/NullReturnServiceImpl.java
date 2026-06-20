package org.dromara.djs.breed.event.nullreturn.service.impl;

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
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.I18nMessages;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.nullreturn.domain.PigAbnormal;
import org.dromara.djs.breed.event.nullreturn.domain.bo.NullReturnBo;
import org.dromara.djs.breed.event.nullreturn.domain.query.NullReturnQuery;
import org.dromara.djs.breed.event.nullreturn.domain.vo.PigAbnormalVo;
import org.dromara.djs.breed.event.nullreturn.mapper.PigAbnormalMapper;
import org.dromara.djs.breed.event.nullreturn.service.INullReturnService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 返空事件 Service 实现（BRD-EVENT-002 NULL_RETURN）。
 *
 * <h3>映射</h3>
 * <p>BO {@code abnormalType} ↔ DB {@code abnormal_type}（CR-20260524-07）：</p>
 * <pre>
 *   abort  → A (流产)  → 状态机 PZ → LC
 *   return → R (返情)  → 状态机 PZ → FQ
 *   idle   → N (空怀)  → 状态机 PZ → KH
 * </pre>
 *
 * <h3>校验</h3>
 * <p>service 提前校验源态必须 PZ（见 ADR-0010），不让无效请求进状态机噪声日志。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NullReturnServiceImpl implements INullReturnService {

    private final PigAbnormalMapper abnormalMapper;
    private final PigMapper pigMapper;
    private final IPigCoreService pigCoreService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigAbnormalVo recordNullReturn(NullReturnBo bo) {
        Objects.requireNonNull(bo, "NullReturnBo must not be null");

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
        // 源态必须 PZ（见 ADR-0010）；非 PZ 直接拒绝，不进状态机
        if (!PigLifecycle.PZ.name().equals(pig.getCurrentStatus())) {
            throw new ServiceException(I18nMessages.t("null_return.source_state.invalid",
                pig.getCurrentStatus()));
        }

        String dbCode = mapBoToDb(bo.getAbnormalType());

        // 1. 写 t_farm_pig_abnormal
        PigAbnormal entity = new PigAbnormal();
        entity.setPigId(pig.getId());
        entity.setEarNo(pig.getEarNo());
        entity.setAbnormalDate(bo.getNullReturnDate());
        entity.setAbnormalType(dbCode);
        entity.setRelatedBreedingId(pig.getMatingId());
        entity.setRemark(bo.getRemark());
        // 录入人员：优先 BO.operatorId（mp EmployeePicker 选）；空则回落登录态
        entity.setOperatorId(bo.getOperatorId() != null ? bo.getOperatorId() : LoginHelper.getUserId());
        entity.setDelFlag("0");
        abnormalMapper.insert(entity);

        // 2. 触发状态机：payload.abnormalType 透传 BO 原值（abort/return/idle）
        Map<String, Object> payload = new HashMap<>(2);
        payload.put("abnormalType", bo.getAbnormalType());

        PigEventBo eventBo = new PigEventBo();
        eventBo.setPigId(pig.getId());
        eventBo.setEventType(PigStatusEvent.NULL_RETURN);
        eventBo.setRelatedEventId(entity.getId());
        eventBo.setEventAt(bo.getNullReturnDate());
        eventBo.setPayload(payload);
        pigCoreService.fireEvent(eventBo);

        log.info("[BRD-EVENT-002] recordNullReturn pigId={} earNo={} abnormalId={} bo={} db={}",
            pig.getId(), pig.getEarNo(), entity.getId(), bo.getAbnormalType(), dbCode);

        // 状态机已流转母猪状态，回读最新 pig 给前端结果卡展示 currentStatus + 日龄/胎次
        Pig fired = pigMapper.selectById(pig.getId());
        PigAbnormalVo vo = toVo(entity);
        enrichFromPig(vo, fired != null ? fired : pig);
        return vo;
    }

    @Override
    public TableDataInfo<PigAbnormalVo> queryPage(NullReturnQuery query, PageQuery pageQuery) {
        LocalDateTime beginAt = query.getBeginDate() != null ? query.getBeginDate().atStartOfDay() : null;
        LocalDateTime endBefore = query.getEndDate() != null ? query.getEndDate().plusDays(1).atStartOfDay() : null;
        LambdaQueryWrapper<PigAbnormal> w = Wrappers.<PigAbnormal>lambdaQuery()
            .eq(query.getPigId() != null, PigAbnormal::getPigId, query.getPigId())
            .like(StringUtils.isNotBlank(query.getEarNo()), PigAbnormal::getEarNo, query.getEarNo())
            .eq(StringUtils.isNotBlank(query.getAbnormalType()),
                PigAbnormal::getAbnormalType, query.getAbnormalType())
            .ge(beginAt != null, PigAbnormal::getAbnormalDate, beginAt)
            .lt(endBefore != null, PigAbnormal::getAbnormalDate, endBefore)
            .orderByDesc(PigAbnormal::getAbnormalDate, PigAbnormal::getId);
        Page<PigAbnormalVo> page = abnormalMapper.selectVoPage(pageQuery.build(), w);
        enrichRows(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 列表 enrich 母猪日龄/胎次/当前状态/持续天数（mp 记录列表多行展示用）。
     *
     * <p>批查去重 pigId 避免 N+1；日龄/持续天数用 LocalDate 在 service 算（避免 SQL 方言）。
     * operatorName 由 VO 上 {@code @Translation} 按 operatorId 翻译，本方法不处理。</p>
     */
    private void enrichRows(java.util.List<PigAbnormalVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        java.util.List<Long> pigIds = rows.stream()
            .map(PigAbnormalVo::getPigId).filter(Objects::nonNull).distinct().toList();
        if (pigIds.isEmpty()) {
            return;
        }
        java.util.Map<Long, Pig> pigById = new java.util.HashMap<>();
        for (Pig p : pigMapper.selectByIds(pigIds)) {
            pigById.put(p.getId(), p);
        }
        for (PigAbnormalVo vo : rows) {
            Pig p = vo.getPigId() == null ? null : pigById.get(vo.getPigId());
            if (p != null) {
                enrichFromPig(vo, p);
            }
        }
    }

    /**
     * 单条 enrich：从 pig 取 parity/current_status，按「异常发生时」快照算派生字段。
     * <p>日龄 = 异常日期 - birthDate(fallback introduceDate)（异常发生时日龄，非今天，避免返空后状态机流转污染）；
     * 配怀持续天数 matingDays = 异常日期 - lastMatingDate（缺 lastMatingDate 留 null）；
     * 持续天数 durationDays = 异常日期至今。基准/异常日期缺失时对应字段留 null。</p>
     */
    private void enrichFromPig(PigAbnormalVo vo, Pig pig) {
        if (pig == null) {
            return;
        }
        vo.setParity(pig.getParity());
        vo.setCurrentStatus(pig.getCurrentStatus());
        LocalDate today = LocalDate.now();
        LocalDate abnormalDay = vo.getAbnormalDate() != null ? vo.getAbnormalDate().toLocalDate() : null;
        LocalDate base = pig.getBirthDate() != null ? pig.getBirthDate() : pig.getIntroduceDate();
        if (base != null && abnormalDay != null) {
            vo.setDayAge((int) Math.max(ChronoUnit.DAYS.between(base, abnormalDay), 0L));
        }
        if (pig.getLastMatingDate() != null && abnormalDay != null) {
            vo.setMatingDays((int) Math.max(ChronoUnit.DAYS.between(pig.getLastMatingDate(), abnormalDay), 0L));
        }
        if (abnormalDay != null) {
            vo.setDurationDays((int) Math.max(ChronoUnit.DAYS.between(abnormalDay, today), 0L));
        }
    }

    /** abort→A / idle→N / return→R（CR-20260524-07）。 */
    private String mapBoToDb(String boType) {
        return switch (boType) {
            case "abort" -> "A";
            case "idle" -> "N";
            case "return" -> "R";
            default -> throw new ServiceException(
                I18nMessages.t("null_return.abnormal_type.invalid", boType));
        };
    }

    private PigAbnormalVo toVo(PigAbnormal e) {
        PigAbnormalVo v = new PigAbnormalVo();
        v.setId(e.getId());
        v.setPigId(e.getPigId());
        v.setEarNo(e.getEarNo());
        v.setAbnormalDate(e.getAbnormalDate());
        v.setAbnormalType(e.getAbnormalType());
        v.setRelatedBreedingId(e.getRelatedBreedingId());
        v.setOperatorId(e.getOperatorId());
        v.setRemark(e.getRemark());
        return v;
    }
}
