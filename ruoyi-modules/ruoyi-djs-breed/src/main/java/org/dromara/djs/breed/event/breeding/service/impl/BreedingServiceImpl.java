package org.dromara.djs.breed.event.breeding.service.impl;

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
import org.dromara.djs.breed.event.breeding.domain.PigBreeding;
import org.dromara.djs.breed.event.breeding.domain.bo.BreedingBo;
import org.dromara.djs.breed.event.breeding.domain.query.BreedingQuery;
import org.dromara.djs.breed.event.breeding.domain.vo.PigBreedingVo;
import org.dromara.djs.breed.event.breeding.mapper.PigBreedingMapper;
import org.dromara.djs.breed.event.breeding.service.IBreedingService;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 配种事件 Service 实现（BRD-EVENT-002 BREED）。
 *
 * <h3>事务边界</h3>
 * <p>{@link #recordBreeding} 标 {@code @Transactional}：INSERT breeding +
 * fireEvent(BREED) 同生共死。fireEvent 内 PigCoreServiceImpl 已包：状态机推进 +
 * 写 status_record + pig.update（含 mating_id） + applyBreedingCounters（mating_count +1 + last_mating_date）。</p>
 *
 * <h3>校验</h3>
 * <p>本场公猪配种（breedingType=1）必填 boar_ear_no；精液产品（breedingType=2）必填 supplier + batch_no。
 * 其他类型 V1 不校验（字典 djs_breeding_type 由 SYS-INIT-002 维护）。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BreedingServiceImpl implements IBreedingService {

    private final PigBreedingMapper breedingMapper;
    private final PigMapper pigMapper;
    private final BarnMapper barnMapper;
    private final PenMapper penMapper;
    private final IPigCoreService pigCoreService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigBreedingVo recordBreeding(BreedingBo bo) {
        Objects.requireNonNull(bo, "BreedingBo must not be null");

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

        validate(bo);

        // 1. 写 t_farm_pig_breeding
        PigBreeding entity = new PigBreeding();
        entity.setPigId(pig.getId());
        entity.setEarNo(pig.getEarNo());
        entity.setBreedingDate(bo.getBreedingDate());
        entity.setBreedingType(bo.getBreedingType());
        entity.setBoarEarNo(bo.getBoarEarNo());
        entity.setSemenSupplier(bo.getSemenSupplier());
        entity.setSemenBatchNo(bo.getSemenBatchNo());
        entity.setParity(Optional.ofNullable(pig.getParity()).orElse(0));
        entity.setBarnName(resolveBarnName(pig.getBarnId()));
        entity.setPenName(resolvePenName(pig.getPenId()));
        entity.setProofOssIds(bo.getProofOssIds());
        entity.setRemark(bo.getRemark());
        entity.setOperatorId(LoginHelper.getUserId());
        entity.setDelFlag("0");
        breedingMapper.insert(entity);

        // 2. 触发状态机（HB/DN/LC/KH/FQ → PZ）+ side effects（mating_id + counters）
        //    payload.breedingDate 给 applyBreedingCounters 用，写入 pig.last_mating_date
        Map<String, Object> payload = new HashMap<>(2);
        payload.put("breedingDate", bo.getBreedingDate().toLocalDate());

        PigEventBo eventBo = new PigEventBo();
        eventBo.setPigId(pig.getId());
        eventBo.setEventType(PigStatusEvent.BREED);
        eventBo.setRelatedEventId(entity.getId());
        eventBo.setEventAt(bo.getBreedingDate());
        eventBo.setPayload(payload);
        pigCoreService.fireEvent(eventBo);

        log.info("[BRD-EVENT-002] recordBreeding pigId={} earNo={} breedingId={} type={} boar={}",
            pig.getId(), pig.getEarNo(), entity.getId(), bo.getBreedingType(), bo.getBoarEarNo());

        return toVo(entity);
    }

    @Override
    public TableDataInfo<PigBreedingVo> queryPage(BreedingQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PigBreeding> w = Wrappers.<PigBreeding>lambdaQuery()
            .eq(query.getPigId() != null, PigBreeding::getPigId, query.getPigId())
            .eq(StringUtils.isNotBlank(query.getEarNo()), PigBreeding::getEarNo, query.getEarNo())
            .eq(StringUtils.isNotBlank(query.getBreedingType()), PigBreeding::getBreedingType, query.getBreedingType())
            .eq(StringUtils.isNotBlank(query.getBoarEarNo()), PigBreeding::getBoarEarNo, query.getBoarEarNo())
            .ge(query.getBeginDate() != null, PigBreeding::getBreedingDate, query.getBeginDate())
            .le(query.getEndDate() != null, PigBreeding::getBreedingDate, query.getEndDate())
            .orderByDesc(PigBreeding::getBreedingDate, PigBreeding::getId);
        Page<PigBreedingVo> page = breedingMapper.selectVoPage(pageQuery.build(), w);
        return TableDataInfo.build(page);
    }

    /** breedingType=1 本场公猪 → 必填 boarEarNo；breedingType=2 精液产品 → 必填 supplier + batch。 */
    private void validate(BreedingBo bo) {
        if ("1".equals(bo.getBreedingType()) && StringUtils.isBlank(bo.getBoarEarNo())) {
            throw new ServiceException(I18nMessages.t("breeding.boar_ear.required"));
        }
        if ("2".equals(bo.getBreedingType())
            && (StringUtils.isBlank(bo.getSemenSupplier()) || StringUtils.isBlank(bo.getSemenBatchNo()))) {
            throw new ServiceException(I18nMessages.t("breeding.semen_info.required"));
        }
    }

    private String resolveBarnName(Long barnId) {
        if (barnId == null) {
            return null;
        }
        Barn b = barnMapper.selectById(barnId);
        return b == null ? null : b.getBarnName();
    }

    private String resolvePenName(Long penId) {
        if (penId == null) {
            return null;
        }
        Pen p = penMapper.selectById(penId);
        return p == null ? null : p.getPenName();
    }

    private PigBreedingVo toVo(PigBreeding e) {
        PigBreedingVo v = new PigBreedingVo();
        v.setId(e.getId());
        v.setPigId(e.getPigId());
        v.setEarNo(e.getEarNo());
        v.setBreedingDate(e.getBreedingDate());
        v.setBreedingType(e.getBreedingType());
        v.setBoarEarNo(e.getBoarEarNo());
        v.setSemenSupplier(e.getSemenSupplier());
        v.setSemenBatchNo(e.getSemenBatchNo());
        v.setParity(e.getParity());
        v.setOperatorId(e.getOperatorId());
        v.setBarnName(e.getBarnName());
        v.setPenName(e.getPenName());
        v.setRemark(e.getRemark());
        return v;
    }
}
