package org.dromara.djs.breed.event.die.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.dromara.djs.breed.event.die.domain.PigDeath;
import org.dromara.djs.breed.event.die.domain.bo.DieBo;
import org.dromara.djs.breed.event.die.domain.query.DieQuery;
import org.dromara.djs.breed.event.die.domain.vo.PigDeathVo;
import org.dromara.djs.breed.event.die.mapper.PigDeathMapper;
import org.dromara.djs.breed.event.die.service.IDieService;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 死亡事件 Service 实现（BRD-EVENT-004 DIE）。
 *
 * <h3>事务边界</h3>
 * <p>{@link #recordDie} 标 {@code @Transactional}：INSERT death + fireEvent(DIE) 同生共死。
 * fireEvent 内 PigCoreServiceImpl 已包：状态机推进 (非 END → END) + status_record + end_reason=DEAD。</p>
 *
 * <h3>校验</h3>
 * <p>service 层强制 {@code ossIds} 非空（多角度凭证至少 1 张）。死亡猪只类型 from pig.pig_type 自动填充。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DieServiceImpl implements IDieService {

    private final PigDeathMapper deathMapper;
    private final PigMapper pigMapper;
    private final BarnMapper barnMapper;
    private final PenMapper penMapper;
    private final IPigCoreService pigCoreService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigDeathVo recordDie(DieBo bo) {
        Objects.requireNonNull(bo, "DieBo must not be null");

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
            throw new ServiceException(I18nMessages.t("die.photo.required"));
        }

        // 1. 写 t_farm_pig_death
        PigDeath entity = new PigDeath();
        entity.setPigId(pig.getId());
        entity.setEarNo(pig.getEarNo());
        entity.setDeathDate(bo.getDeathDate());
        entity.setDeathPigType(pig.getPigType());
        entity.setDeathKind(bo.getDeathKind());
        entity.setDeathReason(bo.getDeathReason());
        entity.setDeathDest(bo.getDeathDest());
        entity.setDeathWeight(bo.getDeathWeight());
        entity.setOssIds(bo.getOssIds());
        entity.setBarnName(resolveBarnName(pig.getBarnId()));
        entity.setPenName(resolvePenName(pig.getPenId()));
        entity.setRemark(bo.getRemark());
        entity.setDelFlag("0");
        deathMapper.insert(entity);

        // 2. 触发状态机 (非 END) → END，end_reason=DEAD
        PigEventBo eventBo = new PigEventBo();
        eventBo.setPigId(pig.getId());
        eventBo.setEventType(PigStatusEvent.DIE);
        eventBo.setRelatedEventId(entity.getId());
        eventBo.setEventAt(bo.getDeathDate());
        pigCoreService.fireEvent(eventBo);

        log.info("[BRD-EVENT-004] recordDie pigId={} earNo={} deathId={} kind={} reason={}",
            pig.getId(), pig.getEarNo(), entity.getId(), bo.getDeathKind(), bo.getDeathReason());

        return toVo(entity);
    }

    @Override
    public TableDataInfo<PigDeathVo> queryPage(DieQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PigDeath> w = Wrappers.<PigDeath>lambdaQuery()
            .eq(query.getPigId() != null, PigDeath::getPigId, query.getPigId())
            .eq(StringUtils.isNotBlank(query.getEarNo()), PigDeath::getEarNo, query.getEarNo())
            .eq(StringUtils.isNotBlank(query.getDeathKind()), PigDeath::getDeathKind, query.getDeathKind())
            .eq(StringUtils.isNotBlank(query.getDeathReason()), PigDeath::getDeathReason, query.getDeathReason())
            .ge(query.getBeginDate() != null, PigDeath::getDeathDate, query.getBeginDate())
            .le(query.getEndDate() != null, PigDeath::getDeathDate, query.getEndDate())
            .orderByDesc(PigDeath::getDeathDate, PigDeath::getId);
        Page<PigDeathVo> page = deathMapper.selectVoPage(pageQuery.build(), w);
        return TableDataInfo.build(page);
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

    private PigDeathVo toVo(PigDeath e) {
        PigDeathVo v = new PigDeathVo();
        v.setId(e.getId());
        v.setPigId(e.getPigId());
        v.setEarNo(e.getEarNo());
        v.setDeathDate(e.getDeathDate());
        v.setDeathPigType(e.getDeathPigType());
        v.setDeathKind(e.getDeathKind());
        v.setDeathReason(e.getDeathReason());
        v.setDeathDest(e.getDeathDest());
        v.setDeathWeight(e.getDeathWeight());
        v.setOssIds(e.getOssIds());
        v.setOperatorId(e.getOperatorId());
        v.setBarnName(e.getBarnName());
        v.setPenName(e.getPenName());
        v.setRemark(e.getRemark());
        return v;
    }
}
