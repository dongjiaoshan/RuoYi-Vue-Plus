package org.dromara.djs.breed.event.breeding.service.impl;

import org.dromara.common.satoken.utils.LoginHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.dromara.djs.breed.core.util.PigAgeUtil;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.breeding.domain.PigBreeding;
import org.dromara.djs.breed.event.breeding.domain.bo.BreedingBo;
import org.dromara.djs.breed.event.breeding.domain.query.BreedingQuery;
import org.dromara.djs.breed.event.breeding.domain.vo.PigBreedingVo;
import org.dromara.djs.breed.event.breeding.mapper.PigBreedingMapper;
import org.dromara.djs.breed.event.breeding.service.IBreedingService;
import org.dromara.djs.breed.breeding.domain.BreedConfig;
import org.dromara.djs.breed.breeding.mapper.BreedConfigMapper;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * 其他类型 V1 不校验（字典 djs_mating_method 由 SYS-INIT-002 维护）。</p>
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
    private final BreedConfigMapper breedConfigMapper;

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
        // r181：本场公猪配种时，校验母系×父系的品种组合 + 品系组合是否已在 t_farm_breed_config 登记；未配置 → 拦截
        validateBreedConfig(pig, bo);

        // 1. 写 t_farm_pig_breeding
        PigBreeding entity = new PigBreeding();
        entity.setPigId(pig.getId());
        entity.setEarNo(pig.getEarNo());
        entity.setBreedingDate(bo.getBreedingDate());
        entity.setBreedingType(bo.getBreedingType());
        entity.setBoarEarNo(bo.getBoarEarNo());
        entity.setSemenCode(bo.getSemenCode());
        entity.setParity(Optional.ofNullable(pig.getParity()).orElse(0));
        entity.setBarnName(resolveBarnName(pig.getBarnId()));
        entity.setPenName(resolvePenName(pig.getPenId()));
        entity.setProofOssIds(bo.getProofOssIds());
        entity.setRemark(bo.getRemark());
        // #20：优先用录入选中的配种人员（EmployeePicker），未选回落当前登录态
        entity.setOperatorId(bo.getOperatorId() != null ? bo.getOperatorId() : LoginHelper.getUserId());
        entity.setDelFlag("0");
        LocalDate _evt = entity.getBreedingDate() != null ? entity.getBreedingDate().toLocalDate() : null;
        entity.setAgeDays(PigAgeUtil.ageDaysAt(pig, _evt));
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
        LocalDateTime beginAt = query.getBeginDate() != null ? query.getBeginDate().atStartOfDay() : null;
        LocalDateTime endBefore = query.getEndDate() != null ? query.getEndDate().plusDays(1).atStartOfDay() : null;
        // 自定义 @Select JOIN t_farm_pig_info 派生 sowStatus/statusDays（裸 selectVoPage 拼不出 JOIN/DATEDIFF）；
        // operatorName 由 VO 上 @Translation 按 operatorId 翻译。
        Page<PigBreedingVo> page = (Page<PigBreedingVo>) breedingMapper.selectBreedingVoPage(
            pageQuery.build(),
            query.getPigId(),
            StringUtils.isNotBlank(query.getEarNo()) ? query.getEarNo() : null,
            StringUtils.isNotBlank(query.getBreedingType()) ? query.getBreedingType() : null,
            StringUtils.isNotBlank(query.getBoarEarNo()) ? query.getBoarEarNo() : null,
            beginAt,
            endBefore);
        return TableDataInfo.build(page);
    }

    /**
     * breedingType=1 本场公猪 → 必填 boarEarNo；非 1（精液类）→ 必填 semenCode（djs_semen 字典 code）。
     * FIX-BREEDING-001 #21：精液改纯字典下拉，去供应商/批号；精液类型口径 = breedingType != '1'。
     */
    private void validate(BreedingBo bo) {
        if ("1".equals(bo.getBreedingType()) && StringUtils.isBlank(bo.getBoarEarNo())) {
            throw new ServiceException(I18nMessages.t("breeding.boar_ear.required"));
        }
        if (!"1".equals(bo.getBreedingType()) && StringUtils.isBlank(bo.getSemenCode())) {
            throw new ServiceException(I18nMessages.t("breeding.semen_code.required"));
        }
    }

    /**
     * r181：本场公猪配种（breedingType=1）时，母系×父系的「品种组合」与「品系组合」须在 {@code t_farm_breed_config} 登记。
     * 任一未登记（含母/父品种或品系码本身为空 → 无法组合）→ 抛「暂不支持这样配种，请检查数据」。
     * 精液类配种（breedingType≠1）无父系猪只 → 跳过；公猪耳号查不到 pig_info → 不在此拦（交原流程）。
     */
    private void validateBreedConfig(Pig sow, BreedingBo bo) {
        if (!"1".equals(bo.getBreedingType()) || StringUtils.isBlank(bo.getBoarEarNo())) {
            return;
        }
        Pig boar = pigMapper.selectOne(new LambdaQueryWrapper<Pig>()
            .eq(Pig::getEarNo, bo.getBoarEarNo())
            .eq(Pig::getDelFlag, "0")
            .orderByDesc(Pig::getId)
            .last("LIMIT 1"));
        if (boar == null) {
            return;
        }
        boolean breedOk = existsBreedConfig(1, sow.getPigBreedCode(), boar.getPigBreedCode());
        boolean strainOk = existsBreedConfig(2, sow.getPigStrainCode(), boar.getPigStrainCode());
        if (!breedOk || !strainOk) {
            throw new ServiceException("暂不支持这样配种，请检查数据");
        }
    }

    /** 育种配置表存在性：{@code breedStrain + 母码 + 父码}（母/父码 2 位零填充对齐配置表）。母或父码为空 → false（视为未配置）。 */
    private boolean existsBreedConfig(int breedStrain, String motherCode, String fatherCode) {
        if (StringUtils.isBlank(motherCode) || StringUtils.isBlank(fatherCode)) {
            return false;
        }
        Long cnt = breedConfigMapper.selectCount(new LambdaQueryWrapper<BreedConfig>()
            .eq(BreedConfig::getBreedStrain, breedStrain)
            .eq(BreedConfig::getMotherCode, pad2(motherCode))
            .eq(BreedConfig::getFatherCode, pad2(fatherCode))
            .eq(BreedConfig::getDelFlag, "0"));
        return cnt != null && cnt > 0;
    }

    /** 品种/品系码 2 位零填充（{@code '1'→'01'}），对齐 t_farm_breed_config 存码格式。 */
    private static String pad2(String code) {
        String c = code == null ? "" : code.trim();
        return c.length() == 1 ? "0" + c : c;
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
        v.setSemenCode(e.getSemenCode());
        v.setParity(e.getParity());
        v.setOperatorId(e.getOperatorId());
        v.setBarnName(e.getBarnName());
        v.setPenName(e.getPenName());
        v.setRemark(e.getRemark());
        return v;
    }
}
