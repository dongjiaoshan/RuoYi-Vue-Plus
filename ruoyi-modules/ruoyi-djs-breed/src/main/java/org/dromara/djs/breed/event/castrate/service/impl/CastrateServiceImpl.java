package org.dromara.djs.breed.event.castrate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.satoken.utils.LoginHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.service.UserService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.I18nMessages;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.castrate.domain.CastrateRecord;
import org.dromara.djs.breed.event.castrate.domain.bo.CastrateBo;
import org.dromara.djs.breed.event.castrate.domain.query.CastrateQuery;
import org.dromara.djs.breed.event.castrate.domain.vo.CastrateRecordVo;
import org.dromara.djs.breed.event.castrate.mapper.CastrateRecordMapper;
import org.dromara.djs.breed.event.castrate.service.ICastrateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 阉割事件 Service 实现（BRD-EVENT-004 CASTRATE）。
 *
 * <h3>性别校验</h3>
 * <p>service 层提前校验 pig_sex='M'：状态机内部也会兜底校验，但 service 层早出错可避免脏 INSERT。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CastrateServiceImpl implements ICastrateService {

    private final CastrateRecordMapper castrateMapper;
    private final PigMapper pigMapper;
    private final IPigCoreService pigCoreService;
    private final DictService dictService;
    private final UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CastrateRecordVo recordCastrate(CastrateBo bo) {
        Objects.requireNonNull(bo, "CastrateBo must not be null");

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

        if (!"M".equals(pig.getPigSex())) {
            throw new ServiceException(I18nMessages.t("castrate.male_only", pig.getId()));
        }
        // 仅允许阉割「公的仔猪 + 育肥猪」，排除种公猪（boar）。
        if (!"piglet".equals(pig.getPigType()) && !"fattening".equals(pig.getPigType())) {
            throw new ServiceException("种公猪不可阉割");
        }

        CastrateRecord entity = new CastrateRecord();
        entity.setPigId(pig.getId());
        entity.setEarNo(pig.getEarNo());
        entity.setCastrateDate(bo.getCastrateDate());
        entity.setCastrater(StringUtils.isNotBlank(bo.getCastrater()) ? bo.getCastrater().trim() : null);
        entity.setRemark(bo.getRemark());
        entity.setOperatorId(LoginHelper.getUserId());
        entity.setDelFlag("0");
        castrateMapper.insert(entity);

        PigEventBo eventBo = new PigEventBo();
        eventBo.setPigId(pig.getId());
        eventBo.setEventType(PigStatusEvent.CASTRATE);
        eventBo.setRelatedEventId(entity.getId());
        eventBo.setEventAt(bo.getCastrateDate());
        pigCoreService.fireEvent(eventBo);

        log.info("[BRD-EVENT-004] recordCastrate pigId={} earNo={} castrateId={}",
            pig.getId(), pig.getEarNo(), entity.getId());

        return toVo(entity);
    }

    @Override
    public TableDataInfo<CastrateRecordVo> queryPage(CastrateQuery query, PageQuery pageQuery) {
        LocalDateTime beginAt = query.getBeginDate() != null ? query.getBeginDate().atStartOfDay() : null;
        LocalDateTime endBefore = query.getEndDate() != null ? query.getEndDate().plusDays(1).atStartOfDay() : null;
        LambdaQueryWrapper<CastrateRecord> w = Wrappers.<CastrateRecord>lambdaQuery()
            .eq(query.getPigId() != null, CastrateRecord::getPigId, query.getPigId())
            .like(StringUtils.isNotBlank(query.getEarNo()), CastrateRecord::getEarNo, query.getEarNo())
            .ge(beginAt != null, CastrateRecord::getCastrateDate, beginAt)
            .lt(endBefore != null, CastrateRecord::getCastrateDate, endBefore)
            .orderByDesc(CastrateRecord::getCastrateDate, CastrateRecord::getId);
        Page<CastrateRecordVo> page = castrateMapper.selectVoPage(pageQuery.build(), w);
        enrichRecords(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 216：记录卡 enrich——按 pigId 批量查猪只，回填性别 / 品种 / 日龄；按 castrater(userId) 回填员工名。
     * 批量查避免 N+1：一次性 selectBatchIds(pigId 集合) 查猪只，再按去重 userId 查员工名。
     */
    private void enrichRecords(List<CastrateRecordVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        // 1. 批量查猪只（pigId → Pig），回填性别 / 品种 / 日龄
        List<Long> pigIds = records.stream()
            .map(CastrateRecordVo::getPigId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, Pig> pigMap = pigIds.isEmpty()
            ? Map.of()
            : pigMapper.selectBatchIds(pigIds).stream()
                .collect(Collectors.toMap(Pig::getId, p -> p, (a, b) -> a));
        // 2. 翻译员工名（castrater 存 userId；去重后逐 id 查回，userId→nickname 映射）
        Map<String, String> nicknameMap = resolveCastraterNames(records);
        for (CastrateRecordVo v : records) {
            Pig pig = v.getPigId() != null ? pigMap.get(v.getPigId()) : null;
            if (pig != null) {
                v.setPigSexLabel(translateDict("djs_pig_sex", pig.getPigSex()));
                v.setPigBreedLabel(translateDict("djs_pig_breed", pig.getPigBreedCode()));
                v.setAgeDays(calcAgeDays(pig.getBirthDate()));
            }
            if (StringUtils.isNotBlank(v.getCastrater())) {
                v.setCastraterName(nicknameMap.get(v.getCastrater().trim()));
            }
        }
    }

    /** 字典翻译；翻不到回落 code。 */
    private String translateDict(String dictType, String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String label = dictService.getDictLabel(dictType, code);
        return StringUtils.isNotBlank(label) ? label : code;
    }

    /** 日龄（天）= NOW - birthDate；birthDate 为 null 返 null。 */
    private Integer calcAgeDays(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(birthDate, LocalDate.now());
    }

    /**
     * 收集去重的 castrater(userId) → userId→nickname。
     * 逐个 userId 单独查 selectNicknameByIds（其内部按 isNotBlank 过滤会丢空昵称、破坏批量位置映射，
     * 故按单 id 查回再 put，保证 userId↔nickname 对齐；本页记录页大小有限，无 N+1 性能风险）。
     */
    private Map<String, String> resolveCastraterNames(List<CastrateRecordVo> records) {
        List<String> ids = records.stream()
            .map(CastrateRecordVo::getCastrater)
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .distinct()
            .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> map = new java.util.HashMap<>();
        for (String id : ids) {
            String name = userService.selectNicknameByIds(id);
            if (StringUtils.isNotBlank(name)) {
                map.put(id, name);
            }
        }
        return map;
    }

    private CastrateRecordVo toVo(CastrateRecord e) {
        CastrateRecordVo v = new CastrateRecordVo();
        v.setId(e.getId());
        v.setPigId(e.getPigId());
        v.setEarNo(e.getEarNo());
        v.setCastrateDate(e.getCastrateDate());
        v.setOperatorId(e.getOperatorId());
        v.setCastrater(e.getCastrater());
        v.setRemark(e.getRemark());
        return v;
    }
}
