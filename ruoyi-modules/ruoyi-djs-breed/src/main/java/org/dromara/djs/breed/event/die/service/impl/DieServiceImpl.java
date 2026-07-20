package org.dromara.djs.breed.event.die.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.satoken.utils.LoginHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.service.OssService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.util.PigAgeUtil;
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
import org.dromara.djs.breed.event.eartag.domain.PigPigletno;
import org.dromara.djs.breed.event.eartag.mapper.PigPigletnoMapper;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final OssService ossService;
    private final DictService dictService;
    private final PigPigletnoMapper pigPigletnoMapper;

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
        entity.setOperatorId(LoginHelper.getUserId());
        entity.setRecorderId(bo.getRecorderId());
        entity.setDelFlag("0");
        LocalDate _evt = entity.getDeathDate() != null ? entity.getDeathDate().toLocalDate() : null;
        entity.setAgeDays(PigAgeUtil.ageDaysAt(pig, _evt));
        deathMapper.insert(entity);

        // 2. 触发状态机 (非 END) → END，end_reason=DEAD
        PigEventBo eventBo = new PigEventBo();
        eventBo.setPigId(pig.getId());
        eventBo.setEventType(PigStatusEvent.DIE);
        eventBo.setRelatedEventId(entity.getId());
        eventBo.setEventAt(bo.getDeathDate());
        pigCoreService.fireEvent(eventBo);

        // 3. 仔猪死亡同事务软删其耳号日志行（t_farm_pig_pigletno），非仔猪不动
        softDeletePigletnoIfPiglet(pig);

        log.info("[BRD-EVENT-004] recordDie pigId={} earNo={} deathId={} kind={} reason={}",
            pig.getId(), pig.getEarNo(), entity.getId(), bo.getDeathKind(), bo.getDeathReason());

        return toVo(entity);
    }

    /**
     * 仔猪死亡时软删其在 {@code t_farm_pig_pigletno} 的耳号日志行（按 pig_id 精确匹配）。
     *
     * <p>仔猪在系统里是「pig 主表行(pig_type='piglet') + pigletno 日志行」两份，pigletno 行通过
     * {@link PigPigletno#getPigId} 反向指向 {@code t_farm_pig_info.id}。死亡只软删 pig 主表会让
     * pigletno 视图残留耳号，故同事务一并软删。{@code del_flag} 标 {@code @TableLogic}，{@code delete}
     * 经逻辑删除拦截器自动转为 {@code update set del_flag='1'}，不物理删行。</p>
     *
     * <p>幂等：非 piglet 类型不执行；该 pigId 无对应 pigletno 行时 delete 影响 0 行，不报错。</p>
     */
    private void softDeletePigletnoIfPiglet(Pig pig) {
        if (!"piglet".equals(pig.getPigType())) {
            return;
        }
        int deleted = pigPigletnoMapper.delete(
            Wrappers.<PigPigletno>lambdaQuery().eq(PigPigletno::getPigId, pig.getId()));
        log.info("[BRD-EVENT-004] piglet die soft-delete pigletno pigId={} earNo={} deletedRows={}",
            pig.getId(), pig.getEarNo(), deleted);
    }

    @Override
    public TableDataInfo<PigDeathVo> queryPage(DieQuery query, PageQuery pageQuery) {
        LocalDateTime beginAt = query.getBeginDate() != null ? query.getBeginDate().atStartOfDay() : null;
        LocalDateTime endBefore = query.getEndDate() != null ? query.getEndDate().plusDays(1).atStartOfDay() : null;
        LambdaQueryWrapper<PigDeath> w = Wrappers.<PigDeath>lambdaQuery()
            .eq(query.getPigId() != null, PigDeath::getPigId, query.getPigId())
            .like(StringUtils.isNotBlank(query.getEarNo()), PigDeath::getEarNo, query.getEarNo())
            .eq(StringUtils.isNotBlank(query.getDeathKind()), PigDeath::getDeathKind, query.getDeathKind())
            .eq(StringUtils.isNotBlank(query.getDeathReason()), PigDeath::getDeathReason, query.getDeathReason())
            .ge(beginAt != null, PigDeath::getDeathDate, beginAt)
            .lt(endBefore != null, PigDeath::getDeathDate, endBefore)
            .orderByDesc(PigDeath::getDeathDate, PigDeath::getId);
        Page<PigDeathVo> page = deathMapper.selectVoPage(pageQuery.build(), w);
        // 死亡照片 ossIds → 可访问 URL 列表（mp <image> 无法携 Bearer token 取鉴权下载端点，故后端预解析）
        page.getRecords().forEach(v -> v.setImageUrls(resolveImageUrls(v.getOssIds())));
        enrichPigInfo(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 批量回填死亡猪只性别 + 死亡时日龄 + 品系/品种中文名（避免 N+1：去重 pigId 一次性批查 t_farm_pig_info）。
     *
     * <p>日龄按「事件时」口径 = {@code deathDate - birth_date}（死亡当时日龄）；birth_date 缺时回落
     * introduce_date，两者均空 → null。品系/品种名走主数据 t_farm_breed_info 优先 → 字典回落
     * （{@link IPigCoreService#loadBreedStrainNameMap} 预载一次）。猪只可能已物理删/无 birth_date → 对应字段降级 null。</p>
     */
    private void enrichPigInfo(List<PigDeathVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> pigIds = rows.stream().map(PigDeathVo::getPigId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        if (pigIds.isEmpty()) {
            return;
        }
        Map<Long, Pig> pigById = pigMapper.selectBatchIds(pigIds).stream()
            .filter(p -> p.getId() != null)
            .collect(Collectors.toMap(Pig::getId, Function.identity(), (a, b) -> a));
        Map<String, String> breedNameMap = pigCoreService.loadBreedStrainNameMap(1);
        Map<String, String> strainNameMap = pigCoreService.loadBreedStrainNameMap(2);
        for (PigDeathVo vo : rows) {
            Pig p = pigById.get(vo.getPigId());
            if (p == null) {
                continue;
            }
            vo.setPigSex(p.getPigSex());
            // 事件时日龄：死亡当时日龄 = deathDate - birth_date（deathDate 缺时回落 NOW）
            LocalDate eventDate = vo.getDeathDate() != null ? vo.getDeathDate().toLocalDate() : LocalDate.now();
            vo.setAgeDays(calcAgeDays(p, eventDate));
            vo.setPigStrainName(resolveBreedStrainName(strainNameMap, p.getPigStrainCode()));
            vo.setPigBreedName(resolveBreedStrainName(breedNameMap, p.getPigBreedCode()));
        }
    }

    /** 日龄（天）= eventDate - birth_date（缺 birth_date 回落 introduce_date）；两者均空 → null。 */
    private Integer calcAgeDays(Pig p, LocalDate eventDate) {
        LocalDate base = p.getBirthDate() != null ? p.getBirthDate() : p.getIntroduceDate();
        if (base == null || eventDate == null) {
            return null;
        }
        return (int) Math.max(ChronoUnit.DAYS.between(base, eventDate), 0L);
    }

    /** 品种/品系 code→中文名：主数据 t_farm_breed_info 优先 → 字典回落 → 原始 code 回落（缺 code 返 null）。 */
    private String resolveBreedStrainName(Map<String, String> infoNameMap, String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String name = infoNameMap.get(code);
        if (StringUtils.isNotBlank(name)) {
            return name;
        }
        return code;
    }

    /**
     * 死亡照片 ossId（逗号分隔）→ 可访问 URL 列表（无照片返空 list）。
     * <p>参照 {@code PigIntroServiceImpl.resolveProofUrls}：{@link OssService#selectUrlByIds}
     * 返逗号拼接 URL 串，按 {@code ,} 拆成 list 供 mp {@code <image>} 直接渲染。</p>
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
        v.setImageUrls(resolveImageUrls(e.getOssIds()));
        v.setOperatorId(e.getOperatorId());
        v.setRecorderId(e.getRecorderId());
        v.setBarnName(e.getBarnName());
        v.setPenName(e.getPenName());
        v.setRemark(e.getRemark());
        return v;
    }
}
