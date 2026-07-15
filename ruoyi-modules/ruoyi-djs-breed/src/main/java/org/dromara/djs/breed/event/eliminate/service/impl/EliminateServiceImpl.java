package org.dromara.djs.breed.event.eliminate.service.impl;

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
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.I18nMessages;
import org.dromara.djs.breed.core.util.PigAgeUtil;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.eliminate.domain.PigCulling;
import org.dromara.djs.breed.event.eliminate.domain.bo.EliminateBo;
import org.dromara.djs.breed.event.eliminate.domain.query.EliminateQuery;
import org.dromara.djs.breed.event.eliminate.domain.vo.PigCullingVo;
import org.dromara.djs.breed.event.eliminate.mapper.PigCullingMapper;
import org.dromara.djs.breed.event.eliminate.service.IEliminateService;
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
    private final BarnMapper barnMapper;
    private final PenMapper penMapper;
    private final IPigCoreService pigCoreService;
    private final OssService ossService;
    private final DictService dictService;

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
        LocalDate _evt = entity.getCullingDate() != null ? entity.getCullingDate().toLocalDate() : null;
        entity.setAgeDays(PigAgeUtil.ageDaysAt(pig, _evt));
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
        enrichPigInfo(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 批量回填淘汰猪只性别 + 猪只类型中文 + 淘汰时日龄 + 品系/品种中文名 + 栋舍/栏位名（避免 N+1：去重 pigId 一次性批查 t_farm_pig_info）。
     *
     * <p>日龄按「事件时」口径 = {@code cullingDate - birth_date}（淘汰当时日龄）；birth_date 缺时回落
     * introduce_date，两者均空 → null。品系/品种名走主数据 t_farm_breed_info 优先 → 字典回落
     * （{@link IPigCoreService#loadBreedStrainNameMap} 预载一次）。栋舍/栏位名按猪只当前 barn_id/pen_id
     * 去重批查解析。猪只可能已物理删 → 对应字段降级 null。参照 die 模块同款 enrich 写法。</p>
     */
    private void enrichPigInfo(List<PigCullingVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> pigIds = rows.stream().map(PigCullingVo::getPigId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        if (pigIds.isEmpty()) {
            return;
        }
        Map<Long, Pig> pigById = pigMapper.selectBatchIds(pigIds).stream()
            .filter(p -> p.getId() != null)
            .collect(Collectors.toMap(Pig::getId, Function.identity(), (a, b) -> a));
        Map<String, String> breedNameMap = pigCoreService.loadBreedStrainNameMap(1);
        Map<String, String> strainNameMap = pigCoreService.loadBreedStrainNameMap(2);
        Map<Long, String> barnNameById = loadBarnNameMap(pigById.values());
        Map<Long, String> penNameById = loadPenNameMap(pigById.values());
        for (PigCullingVo vo : rows) {
            Pig p = pigById.get(vo.getPigId());
            if (p == null) {
                continue;
            }
            vo.setPigSex(p.getPigSex());
            vo.setPigTypeName(toPigTypeName(p.getPigType()));
            // 事件时日龄：淘汰当时日龄 = cullingDate - birth_date（cullingDate 缺时回落 NOW）
            LocalDate eventDate = vo.getCullingDate() != null ? vo.getCullingDate().toLocalDate() : LocalDate.now();
            vo.setAgeDays(calcAgeDays(p, eventDate));
            vo.setPigStrainName(resolveBreedStrainName(strainNameMap, p.getPigStrainCode()));
            vo.setPigBreedName(resolveBreedStrainName(breedNameMap, p.getPigBreedCode()));
            vo.setBarnName(p.getBarnId() != null ? barnNameById.get(p.getBarnId()) : null);
            vo.setPenName(p.getPenId() != null ? penNameById.get(p.getPenId()) : null);
        }
    }

    /** 去重批查栋舍名（barnId → barnName）；空集返空 map。 */
    private Map<Long, String> loadBarnNameMap(java.util.Collection<Pig> pigs) {
        Set<Long> barnIds = pigs.stream().map(Pig::getBarnId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        if (barnIds.isEmpty()) {
            return Map.of();
        }
        return barnMapper.selectBatchIds(barnIds).stream()
            .filter(b -> b.getId() != null && b.getBarnName() != null)
            .collect(Collectors.toMap(Barn::getId, Barn::getBarnName, (a, b) -> a));
    }

    /** 去重批查栏位名（penId → penName）；空集返空 map。 */
    private Map<Long, String> loadPenNameMap(java.util.Collection<Pig> pigs) {
        Set<Long> penIds = pigs.stream().map(Pig::getPenId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        if (penIds.isEmpty()) {
            return Map.of();
        }
        return penMapper.selectBatchIds(penIds).stream()
            .filter(p -> p.getId() != null && p.getPenName() != null)
            .collect(Collectors.toMap(Pen::getId, Pen::getPenName, (a, b) -> a));
    }

    /** 日龄（天）= eventDate - birth_date（缺 birth_date 回落 introduce_date）；两者均空 → null。 */
    private Integer calcAgeDays(Pig p, LocalDate eventDate) {
        LocalDate base = p.getBirthDate() != null ? p.getBirthDate() : p.getIntroduceDate();
        if (base == null || eventDate == null) {
            return null;
        }
        return (int) Math.max(ChronoUnit.DAYS.between(base, eventDate), 0L);
    }

    /**
     * 猪只类型 code → 中文（种母猪 / 种公猪 / 育肥猪 / 仔猪，口径同 growth toPigTypeName / die deathPigType）。
     * 字典 djs_pig_type 标签为「母猪 / 公猪」，契约要求带「种」前缀，故显式映射保证一致；未知 code → null。
     */
    private String toPigTypeName(String pigType) {
        if (StringUtils.isBlank(pigType)) {
            return null;
        }
        return switch (pigType) {
            case "sow" -> "种母猪";
            case "boar" -> "种公猪";
            case "fattening" -> "育肥猪";
            case "piglet" -> "仔猪";
            default -> null;
        };
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
