package org.dromara.djs.breed.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.PigStatusRecord;
import org.dromara.djs.breed.core.domain.bo.PigCreateBo;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.domain.query.PigQuery;
import org.dromara.djs.breed.core.domain.query.PigStatusRecordQuery;
import org.dromara.djs.breed.core.domain.vo.BoarMatingCountVo;
import org.dromara.djs.breed.core.domain.vo.PigBarnCountVo;
import org.dromara.djs.breed.core.domain.vo.PigDetailVo;
import org.dromara.djs.breed.core.domain.vo.PigEarTagMapVo;
import org.dromara.djs.breed.core.domain.vo.PigIntroDetailVo;
import org.dromara.djs.breed.core.domain.vo.PigLitterAggVo;
import org.dromara.djs.breed.core.domain.vo.PigSearchVo;
import org.dromara.djs.breed.core.domain.vo.PigStatusRecordVo;
import org.dromara.djs.breed.core.domain.vo.PigVo;
import org.dromara.djs.breed.core.enums.PigEndReason;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.event.PigStateChangedEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.mapper.PigStatusRecordMapper;
import org.dromara.djs.breed.core.service.I18nMessages;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.core.service.PigStateMachine;
import org.dromara.djs.breed.core.domain.vo.PigLastFarrowVo;
import org.dromara.djs.breed.breeding.domain.BreedInfo;
import org.dromara.djs.breed.breeding.mapper.BreedInfoMapper;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.dromara.djs.breed.production.service.IProductionCycleConfigService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.dromara.djs.breed.core.enums.PigStatusEvent.BREED;
import static org.dromara.djs.breed.core.enums.PigStatusEvent.DIE;
import static org.dromara.djs.breed.core.enums.PigStatusEvent.ELIMINATE;
import static org.dromara.djs.breed.core.enums.PigStatusEvent.FARROW;
import static org.dromara.djs.breed.core.enums.PigStatusEvent.SLAUGHTER;
import static org.dromara.djs.breed.core.enums.PigStatusEvent.TRANSFER;

/**
 * 猪只主数据 + 状态机核心 Service 实现（BRD-CORE-001 ★ 业务心脏）。
 *
 * <p>{@link #fireEvent} 是整个养殖闭环的状态推进枢纽。所有 BRD-EVENT-* 子 ticket
 * 必须先 INSERT 自己的业务事件表（如 t_farm_pig_breeding）拿到 PK，再调本方法。</p>
 *
 * <p><b>事务边界</b>：{@code @Transactional} 保证 status_record + pig.update 同生共死。
 * Spring event 在事务提交后由 listener 通过 {@code @TransactionalEventListener(AFTER_COMMIT)}
 * 异步处理；本方法内 {@code publishEvent} 是同步调用，但 listener 自身可以 async 化。</p>
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Slf4j
@Service
public class PigCoreServiceImpl implements IPigCoreService {

    private static final int RECENT_HISTORY_LIMIT = 20;
    private static final int LIST_HISTORY_LIMIT = 200;

    private final PigMapper pigMapper;
    private final PigStatusRecordMapper statusRecordMapper;
    private final PigStateMachine stateMachine;
    private final ApplicationEventPublisher eventPublisher;
    private final BarnMapper barnMapper;
    private final PenMapper penMapper;
    private final DictService dictService;
    private final IProductionCycleConfigService productionCycleConfigService;
    private final BreedInfoMapper breedInfoMapper;

    public PigCoreServiceImpl(PigMapper pigMapper,
                              PigStatusRecordMapper statusRecordMapper,
                              PigStateMachine stateMachine,
                              ApplicationEventPublisher eventPublisher,
                              BarnMapper barnMapper,
                              PenMapper penMapper,
                              DictService dictService,
                              IProductionCycleConfigService productionCycleConfigService,
                              BreedInfoMapper breedInfoMapper) {
        this.pigMapper = pigMapper;
        this.statusRecordMapper = statusRecordMapper;
        this.stateMachine = stateMachine;
        this.eventPublisher = eventPublisher;
        this.barnMapper = barnMapper;
        this.penMapper = penMapper;
        this.dictService = dictService;
        this.productionCycleConfigService = productionCycleConfigService;
        this.breedInfoMapper = breedInfoMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigStatusRecordVo fireEvent(PigEventBo bo) {
        Objects.requireNonNull(bo, "PigEventBo must not be null");
        if (bo.getPigId() == null || bo.getEventType() == null) {
            throw new ServiceException(I18nMessages.t("pig.event.bo_required"));
        }

        Pig pig = pigMapper.selectById(bo.getPigId());
        if (pig == null) {
            throw new ServiceException(I18nMessages.t("pig.not_found", bo.getPigId()));
        }

        // from 可为 null：育肥猪 / 仔猪 / 公猪等非种母猪类型空状态（''/NULL），仍可终止 / 转栏 / 阉割。
        String curStatus = pig.getCurrentStatus();
        PigLifecycle from = StringUtils.isBlank(curStatus) ? null : parseLifecycle(curStatus, pig.getId());
        PigLifecycle to = stateMachine.nextStatus(from, bo.getEventType(), pig.getPigSex(), bo.getPayload());

        LocalDateTime eventAt = Optional.ofNullable(bo.getEventAt()).orElseGet(LocalDateTime::now);

        // 1. 写 status_record
        PigStatusRecord record = new PigStatusRecord();
        record.setPigId(pig.getId());
        record.setEarNo(pig.getEarNo());
        // new_status NOT NULL：空状态（非种母猪）写 ''（非 null）。old_status 可空，空前态写 null（与 createPig/internalIntro 一致）。
        record.setOldStatus(from == null ? null : from.name());
        record.setNewStatus(to == null ? "" : to.name());
        record.setEventType(bo.getEventType().name());
        record.setRelatedEventId(bo.getRelatedEventId());
        record.setChangeTime(eventAt);
        record.setDurationDays(calcDurationDays(pig.getStatusStartedAt(), eventAt));
        statusRecordMapper.insert(record);

        // 2. 更新 pig（同事务，乐观锁强制）。Objects.equals 容空：空状态 NO_CHANGE 事件 to==from==null 不改。
        if (!Objects.equals(to, from)) {
            pig.setCurrentStatus(to.name());
            pig.setStatusStartedAt(eventAt);
        }
        applyEventSideEffects(pig, bo, to);
        int affected = pigMapper.updateById(pig);
        if (affected == 0) {
            // MP @Version 版本号不匹配会返 0；抛 ServiceException 让上游业务决定重试 / 降级
            throw new ServiceException(I18nMessages.t("pig.update.optimistic_lock_conflict", pig.getId()));
        }

        // 2b. BREED 事件后置 wrapper-only update：累计配种次数 +1 + 最近配种日期
        //     拆为两步是为避免与 @Version 主 update 冲突：mating_count 走 setSql 原子加法，
        //     即使并发 BREED 也能正确递增（不依赖 read-modify-write）。
        if (bo.getEventType() == BREED) {
            applyBreedingCounters(pig.getId(), bo);
        }

        // 3. 发布 Spring event（下游 dashboard / 推送 / 燎毛各自订阅）
        eventPublisher.publishEvent(new PigStateChangedEvent(this, record, pig, from, to));

        log.info("[BRD-CORE-001] fireEvent pigId={} earNo={} event={} {} -> {} relatedEventId={}",
            pig.getId(), pig.getEarNo(), bo.getEventType(), from, to, bo.getRelatedEventId());

        return toRecordVo(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Pig createPig(PigCreateBo bo) {
        Objects.requireNonNull(bo, "PigCreateBo must not be null");
        if (StringUtils.isBlank(bo.getEarNo()) || StringUtils.isBlank(bo.getPigSex()) || StringUtils.isBlank(bo.getPigType())) {
            throw new ServiceException(I18nMessages.t("pig.create.required_fields_missing"));
        }

        // 初始 lifecycle（ADR-0016）：仅种母猪(sow)→后备 HB；非种母猪类型(boar/piglet/fattening)空状态('')。
        PigLifecycle initial = "sow".equals(bo.getPigType()) ? PigLifecycle.HB : null;
        String initialStatus = initial == null ? "" : initial.name();

        LocalDateTime now = LocalDateTime.now();

        Pig pig = new Pig();
        pig.setEarNo(bo.getEarNo());
        pig.setEarTag(bo.getEarTag());
        pig.setLifecycleId(Optional.ofNullable(bo.getLifecycleId()).orElse(1));
        pig.setRecyclable(Optional.ofNullable(bo.getRecyclable()).orElse(0));
        pig.setPigSex(bo.getPigSex());
        pig.setPigType(bo.getPigType());
        pig.setPigBreedCode(bo.getPigBreedCode());
        pig.setPigStrainCode(bo.getPigStrainCode());
        pig.setCurrentStatus(initialStatus);
        pig.setStatusStartedAt(now);
        pig.setFatherEar(bo.getFatherEar());
        pig.setMotherEar(bo.getMotherEar());
        pig.setBirthDate(bo.getBirthDate());
        pig.setIntroduceDate(bo.getIntroduceDate());
        pig.setIntroduceType(bo.getIntroduceType());
        pig.setSupplierId(bo.getSupplierId());
        pig.setParity(0);
        pig.setBarnId(bo.getBarnId());
        pig.setPenId(bo.getPenId());
        pig.setIsAppointed(0);
        pig.setRemark(bo.getRemark());
        pig.setDelFlag("0");
        pig.setDelUnique(0L);
        // version 由 MP @Version 自动初始化为 0；显式赋一次防底层默认行为差异
        pig.setVersion(0);
        pigMapper.insert(pig);

        // 写初始 status_record（INTRO 事件，old_status=null）
        PigStatusRecord record = new PigStatusRecord();
        record.setPigId(pig.getId());
        record.setEarNo(pig.getEarNo());
        record.setOldStatus(null);
        record.setNewStatus(initialStatus);   // new_status NOT NULL：非种母猪空状态写 ''（非 null）
        record.setEventType(PigStatusEvent.INTRO.name());
        record.setChangeTime(now);
        statusRecordMapper.insert(record);

        eventPublisher.publishEvent(new PigStateChangedEvent(this, record, pig, null, initial));

        log.info("[BRD-CORE-001] createPig pigId={} earNo={} initial={} pigType={}",
            pig.getId(), pig.getEarNo(), initial, pig.getPigType());

        return pig;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void internalIntroToReserve(Long pigId) {
        // 挂调用方（introduceInternal）事务：REQUIRED 默认行为，与 intro insert 同生共死。
        Pig pig = pigMapper.selectById(pigId);
        if (pig == null) {
            throw new ServiceException(I18nMessages.t("pig.not_found", pigId));
        }
        // 仅对 fattening 来源猪生效；已重定为 sow/boar（含本方法幂等重跑）或 piglet → 跳过
        if (!"fattening".equals(pig.getPigType())) {
            return;
        }
        // 终态（END）肥猪已死亡 / 出栏，不可转为种猪
        if (StringUtils.isNotBlank(pig.getCurrentStatus())
            && parseLifecycle(pig.getCurrentStatus(), pig.getId()).isTerminal()) {
            return;
        }

        // 内部引种 = 把场内肥猪转为种猪：按性别重定 pig_type + 初始种猪态（邓博 2026-06-18：
        // 否则母肥猪转后备后 pig_type 仍 fattening，配种选猪（pig_type='sow'）里看不到）。
        //   母 → sow / HB（后备母猪，可进配种）；公 → boar / 空状态 ''（种公猪无繁殖状态，ADR-0016）。
        boolean male = "M".equals(pig.getPigSex());
        String newType = male ? "boar" : "sow";
        String newStatus = male ? "" : PigLifecycle.HB.name();
        PigLifecycle newStatusEnum = male ? null : PigLifecycle.HB;
        // current_status 空（旧 import 脏值）→ old_status=null 不抛 parseLifecycle；否则取原态
        String oldStatus = StringUtils.isBlank(pig.getCurrentStatus()) ? null : pig.getCurrentStatus();

        LocalDateTime now = LocalDateTime.now();
        PigStatusRecord record = new PigStatusRecord();
        record.setPigId(pig.getId());
        record.setEarNo(pig.getEarNo());
        record.setOldStatus(oldStatus);
        record.setNewStatus(newStatus);
        record.setEventType(PigStatusEvent.INTRO.name());
        record.setChangeTime(now);
        if (oldStatus != null) {
            record.setDurationDays(calcDurationDays(pig.getStatusStartedAt(), now));
        }
        statusRecordMapper.insert(record);

        pig.setPigType(newType);
        pig.setCurrentStatus(newStatus);
        pig.setStatusStartedAt(now);
        int affected = pigMapper.updateById(pig);
        if (affected == 0) {
            throw new ServiceException(I18nMessages.t("pig.update.optimistic_lock_conflict", pig.getId()));
        }

        PigLifecycle fromEnum = oldStatus == null ? null : PigLifecycle.valueOf(oldStatus);
        eventPublisher.publishEvent(new PigStateChangedEvent(this, record, pig, fromEnum, newStatusEnum));

        log.info("[FIX-INTRO-RECLASS] internalIntroToReserve pigId={} earNo={} type fattening->{} status {}->{}",
            pig.getId(), pig.getEarNo(), newType, oldStatus, newStatus);
    }

    @Override
    public PigDetailVo queryDetail(Long pigId) {
        if (pigId == null) {
            throw new ServiceException(I18nMessages.t("pig.id.required"));
        }
        PigVo vo = pigMapper.selectVoById(pigId);
        if (vo == null) {
            throw new ServiceException(I18nMessages.t("pig.not_found", pigId));
        }
        enrichBarnPenCodes(List.of(vo));
        enrichParentEarTags(List.of(vo));
        PigDetailVo detail = toDetailVo(vo);
        LambdaQueryWrapper<PigStatusRecord> w = new LambdaQueryWrapper<PigStatusRecord>()
            .eq(PigStatusRecord::getPigId, pigId)
            .orderByDesc(PigStatusRecord::getChangeTime, PigStatusRecord::getId)
            .last("LIMIT " + RECENT_HISTORY_LIMIT);
        detail.setRecentHistory(statusRecordMapper.selectVoList(w));
        return detail;
    }

    @Override
    public PigIntroDetailVo queryIntroDetail(Long pigId) {
        if (pigId == null) {
            throw new ServiceException(I18nMessages.t("pig.id.required"));
        }
        Pig pig = pigMapper.selectById(pigId);
        if (pig == null) {
            throw new ServiceException(I18nMessages.t("pig.not_found", pigId));
        }
        PigIntroDetailVo vo = new PigIntroDetailVo();
        vo.setPigId(pig.getId());
        vo.setEarNo(pig.getEarNo());
        vo.setPigSex(pig.getPigSex());
        // 性别 label 硬映射（F→母猪 / M→公猪），pig_sex 是 F/M 与 sys_user_sex(0/1/2) 字典不通用
        vo.setPigSexLabel("F".equals(pig.getPigSex()) ? "母猪" : "M".equals(pig.getPigSex()) ? "公猪" : null);
        vo.setPigBreedCode(pig.getPigBreedCode());
        // #19/#22：品种/品系名优先取 t_farm_breed_info 主表（客户配的权威名 04=国寿黑），字典回落（单详情，按 code 各预载一次）
        vo.setPigBreedLabel(resolveBreedStrainName(loadBreedStrainNameMap(1), "djs_pig_breed", pig.getPigBreedCode()));
        vo.setPigStrainCode(pig.getPigStrainCode());
        vo.setPigStrainLabel(resolveBreedStrainName(loadBreedStrainNameMap(2), "djs_pig_strain", pig.getPigStrainCode()));
        vo.setAgeDays(calcAgeDays(pig, LocalDate.now()));
        // 当前位置：栋舍名 + 栏位名拼接（缺任一则降级，全缺为 null）
        String barnName = pig.getBarnId() != null
            ? Optional.ofNullable(barnMapper.selectById(pig.getBarnId())).map(Barn::getBarnName).orElse(null) : null;
        String penName = pig.getPenId() != null
            ? Optional.ofNullable(penMapper.selectById(pig.getPenId())).map(Pen::getPenName).orElse(null) : null;
        vo.setBarnName(barnName);
        vo.setPenName(penName);
        if (StringUtils.isNotBlank(barnName) || StringUtils.isNotBlank(penName)) {
            vo.setCurrentLocation((StringUtils.isNotBlank(barnName) ? barnName : "")
                + (StringUtils.isNotBlank(penName) ? penName : ""));
        }
        return vo;
    }

    /** 字典翻译；翻不到（字典缺 seed 或 code 空）回落原始 code，避免 mp 端展示空白。 */
    private String translateDictOrCode(String dictType, String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String label = dictService.getDictLabel(dictType, code);
        return StringUtils.isNotBlank(label) ? label : code;
    }

    /**
     * 加载 t_farm_breed_info 主数据 code→中文名 映射（breedStrain 1=品种 / 2=品系）。
     * 列表批量调用前预载一次，避免逐头查（N+1）。
     */
    @Override
    public Map<String, String> loadBreedStrainNameMap(Integer breedStrain) {
        return breedInfoMapper.selectList(new LambdaQueryWrapper<BreedInfo>()
                .eq(BreedInfo::getBreedStrain, breedStrain)
                .eq(BreedInfo::getDelFlag, "0"))
            .stream()
            .filter(b -> StringUtils.isNotBlank(b.getBreedStrainCode()) && StringUtils.isNotBlank(b.getBreedStrainName()))
            .collect(Collectors.toMap(BreedInfo::getBreedStrainCode, BreedInfo::getBreedStrainName, (a, b) -> a));
    }

    /**
     * 品种/品系 code→中文名解析（邓博 2026-06-17 #13：mp 选猪卡「品系」显示中文不显代码）。
     * 主数据权威源 = t_farm_breed_info（外部引种 BreedInfoPicker 写入的 2 位码如 "01"）；
     * 缺则回落字典（djs_pig_breed / djs_pig_strain，历史单位码如 "1"）；再缺回落原始 code。
     */
    private String resolveBreedStrainName(Map<String, String> infoNameMap, String dictType, String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String name = infoNameMap.get(code);
        return StringUtils.isNotBlank(name) ? name : translateDictOrCode(dictType, code);
    }

    @Override
    public TableDataInfo<PigVo> queryPage(PigQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<Pig> wrapper = buildWrapper(query);
        Page<PigVo> page = pigMapper.selectVoPage(pageQuery.build(), wrapper);
        enrichBarnPenCodes(page.getRecords());
        // 母猪列表：批量回填活仔数 / 断奶仔猪数 / 窝均仔数；公猪列表：批量回填配种次数。
        // 按 pigType 分支只在对应 tab 触发对应聚合（避免无谓查询）。
        enrichSowLitterStats(page.getRecords());
        enrichBoarMatingCount(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 母猪列表 enrich：活仔数（SUM(live_born)）/ 断奶仔猪数（SUM(weaned_count)）/ 窝均仔数（活仔÷胎次）。
     * 批量 IN + GROUP BY 各一次回填，避免 N+1。仅对 pig_sex=F 的母猪行计算。
     */
    private void enrichSowLitterStats(List<PigVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> sowIds = rows.stream()
            .filter(v -> "F".equals(v.getPigSex()) && v.getId() != null)
            .map(PigVo::getId)
            .collect(Collectors.toSet());
        if (sowIds.isEmpty()) {
            return;
        }
        Map<Long, Integer> liveBornMap = pigMapper.sumLiveBornByPigIds(sowIds).stream()
            .filter(v -> v.getPigId() != null)
            .collect(Collectors.toMap(PigLitterAggVo::getPigId,
                v -> Optional.ofNullable(v.getTotal()).orElse(0), (a, b) -> a));
        Map<Long, Integer> weanedMap = pigMapper.sumWeanedByPigIds(sowIds).stream()
            .filter(v -> v.getPigId() != null)
            .collect(Collectors.toMap(PigLitterAggVo::getPigId,
                v -> Optional.ofNullable(v.getTotal()).orElse(0), (a, b) -> a));
        for (PigVo vo : rows) {
            if (!"F".equals(vo.getPigSex()) || vo.getId() == null) {
                continue;
            }
            Integer liveBorn = liveBornMap.get(vo.getId());
            vo.setLiveBornCount(liveBorn);
            vo.setWeanedCount(weanedMap.get(vo.getId()));
            // 窝均仔数 = 活仔数 ÷ 胎次；parity=0 / null → null（与活仔=活口径一致）
            Integer parity = vo.getParity();
            if (liveBorn != null && parity != null && parity > 0) {
                vo.setAvgLitterSize(java.math.BigDecimal.valueOf(liveBorn)
                    .divide(java.math.BigDecimal.valueOf(parity), 1, java.math.RoundingMode.HALF_UP));
            }
        }
    }

    /**
     * 公猪列表 enrich：配种次数（COUNT(t_farm_pig_breeding) GROUP BY boar_ear_no）。
     * 配种表按公猪耳号关联（本场公猪配种填 boar_ear_no），批量 IN + GROUP BY 一次回填，避免 N+1。
     * 仅对 pig_sex=M 的公猪行计算。
     */
    private void enrichBoarMatingCount(List<PigVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<String> boarEarNos = rows.stream()
            .filter(v -> "M".equals(v.getPigSex()) && StringUtils.isNotBlank(v.getEarNo()))
            .map(PigVo::getEarNo)
            .collect(Collectors.toSet());
        if (boarEarNos.isEmpty()) {
            return;
        }
        Map<String, Integer> countMap = pigMapper.countBreedingByBoarEarNos(boarEarNos).stream()
            .filter(v -> StringUtils.isNotBlank(v.getBoarEarNo()))
            .collect(Collectors.toMap(BoarMatingCountVo::getBoarEarNo,
                v -> Optional.ofNullable(v.getTotal()).orElse(0), (a, b) -> a));
        for (PigVo vo : rows) {
            if ("M".equals(vo.getPigSex()) && StringUtils.isNotBlank(vo.getEarNo())) {
                // 公猪 matingCount 列原本恒 0（Pig.matingCount 是母猪累计配种计数器，公猪不写），
                // 这里覆盖为按配种记录的真实统计；无配种记录回 0。
                vo.setMatingCount(countMap.getOrDefault(vo.getEarNo(), 0));
            }
        }
    }

    /**
     * 批量回填 barnCode/barnName + penCode/penName（Pig 与 Barn/Pen 不同聚合，VO 出参给用户看
     * code + 中文名而非 snowflake id；列表场景用 selectBatchIds 一次性查，避免 N+1），
     * 并填品种/品系中文名（邓博 #13/#19：t_farm_breed_info 主表权威名优先 → 字典回落 → code 回落，
     * 与选猪 picker 同源；纯字典查会让 2 位码 "12" 翻不到而显示编码）。
     */
    private void enrichBarnPenCodes(List<PigVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> barnIds = rows.stream().map(PigVo::getBarnId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> penIds = rows.stream().map(PigVo::getPenId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Barn> barnMap = barnIds.isEmpty() ? Map.of()
            : barnMapper.selectBatchIds(barnIds).stream()
                .collect(Collectors.toMap(Barn::getId, Function.identity(), (a, b) -> a));
        Map<Long, Pen> penMap = penIds.isEmpty() ? Map.of()
            : penMapper.selectBatchIds(penIds).stream()
                .collect(Collectors.toMap(Pen::getId, Function.identity(), (a, b) -> a));
        // 品种/品系名：主数据 code→名 映射批量预载一次（防逐行查 N+1），与选猪 picker 同口径
        Map<String, String> breedNameMap = loadBreedStrainNameMap(1);
        Map<String, String> strainNameMap = loadBreedStrainNameMap(2);
        for (PigVo vo : rows) {
            if (vo.getBarnId() != null) {
                Barn barn = barnMap.get(vo.getBarnId());
                if (barn != null) {
                    vo.setBarnCode(barn.getBarnCode());
                    vo.setBarnName(barn.getBarnName());
                }
            }
            if (vo.getPenId() != null) {
                Pen pen = penMap.get(vo.getPenId());
                if (pen != null) {
                    vo.setPenCode(pen.getPenCode());
                    vo.setPenName(pen.getPenName());
                }
            }
            // 品种/品系中文名：t_farm_breed_info 主表优先 → 字典回落 → code 回落（2 位码也能翻出名）
            vo.setPigBreedName(resolveBreedStrainName(breedNameMap, "djs_pig_breed", vo.getPigBreedCode()));
            vo.setPigStrainName(resolveBreedStrainName(strainNameMap, "djs_pig_strain", vo.getPigStrainCode()));
        }
    }

    /**
     * 详情 enrich：按 motherEar / fatherEar 短号批量反查父母猪 ear_tag（全版耳号），
     * 回填 {@code motherEarTag / fatherEarTag}（查不到留 null，前端回落短号）。
     * 批量 IN 一次查（详情虽单行，但父母两个短号合并一次查），避免两次单查。
     */
    private void enrichParentEarTags(List<PigVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<String> earNos = new HashSet<>();
        for (PigVo vo : rows) {
            if (StringUtils.isNotBlank(vo.getMotherEar())) {
                earNos.add(vo.getMotherEar());
            }
            if (StringUtils.isNotBlank(vo.getFatherEar())) {
                earNos.add(vo.getFatherEar());
            }
        }
        if (earNos.isEmpty()) {
            return;
        }
        Map<String, String> tagMap = pigMapper.selectEarTagByEarNos(earNos).stream()
            .filter(v -> StringUtils.isNotBlank(v.getEarNo()) && StringUtils.isNotBlank(v.getEarTag()))
            .collect(Collectors.toMap(PigEarTagMapVo::getEarNo, PigEarTagMapVo::getEarTag, (a, b) -> a));
        for (PigVo vo : rows) {
            if (StringUtils.isNotBlank(vo.getMotherEar())) {
                vo.setMotherEarTag(tagMap.get(vo.getMotherEar()));
            }
            if (StringUtils.isNotBlank(vo.getFatherEar())) {
                vo.setFatherEarTag(tagMap.get(vo.getFatherEar()));
            }
        }
    }

    @Override
    public List<PigStatusRecordVo> listHistory(Long pigId) {
        if (pigId == null) {
            throw new ServiceException(I18nMessages.t("pig.id.required"));
        }
        LambdaQueryWrapper<PigStatusRecord> w = new LambdaQueryWrapper<PigStatusRecord>()
            .eq(PigStatusRecord::getPigId, pigId)
            .orderByDesc(PigStatusRecord::getChangeTime, PigStatusRecord::getId)
            .last("LIMIT " + LIST_HISTORY_LIMIT);
        return statusRecordMapper.selectVoList(w);
    }

    @Override
    public List<PigSearchVo> searchByEarKeyword(String earNoKeyword,
                                                String statusFilter,
                                                String sexFilter,
                                                String pigTypeFilter,
                                                String barnCode,
                                                Integer limit,
                                                String dueType,
                                                Boolean excludeNullBarn,
                                                Integer minAgeDays) {
        int effectiveLimit = clampLimit(limit);
        // 出栏选猪：仅返回到龄肥猪（日龄 >= slaughter_age_days）。日龄 = NOW − birth_date（缺则 introduce_date）；
        // 两者均空 → DATEDIFF 为 NULL，比较结果非真 → 自动剔除（无生日的猪不算到龄）。minAgeDays ≤0/null 不过滤。
        boolean applyMinAge = minAgeDays != null && minAgeDays > 0;
        // FIX-BREEDING-001 #23a：配种选猪场景传 excludeNullBarn=true，排除无栋舍归属猪只，
        // 与 countByBarn（barn-count chip）口径对齐（列表数 = 各栋舍 chip 之和）。默认 false 不变。
        boolean dropNullBarn = Boolean.TRUE.equals(excludeNullBarn);
        // statusFilter CSV："HB,PZ" → IN ('HB','PZ')；如果显式含 END（如燎毛工序选已出栏猪），跳过下方 .ne(END) 默认排除
        List<String> statuses = parseStatusFilter(statusFilter);
        boolean callerWantsEnd = statuses.contains(PigLifecycle.END.name());

        // 栋舍 chip 过滤（PigSelectPanel）：barnCode 是业务码，先 resolve 成 barnId 再过滤；
        // 解析不到（无此栋舍）→ 返空，避免误返全量
        Long barnIdFilter = null;
        if (StringUtils.isNotBlank(barnCode)) {
            barnIdFilter = resolveBarnIdByCode(barnCode);
            if (barnIdFilter == null) {
                return Collections.emptyList();
            }
        }

        // 215：pigTypeFilter 支持 CSV（如 'sow,boar' 给生长记录 tab2「其他猪只」=母猪+生产公猪）；
        // 单值时退化为 .eq，CSV 时走 IN。空 → 不限类型。
        List<String> pigTypes = StringUtils.isNotBlank(pigTypeFilter)
            ? Arrays.stream(pigTypeFilter.split(",")).map(String::trim).filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList())
            : Collections.emptyList();

        LambdaQueryWrapper<Pig> w = new LambdaQueryWrapper<Pig>()
            // 排除 current_status 为 null 的数据异常猪（旧 import / 非 createPig 路径写入），
            // 这些猪无 lifecycle，选中后任何事件 parseLifecycle 都会抛 pig.state.invalid → 不进任何 picker 候选
            .isNotNull(Pig::getCurrentStatus)
            // 默认排除 END 猪只——给事件录入 picker 用（配种 / 转栏等 END 不能再触发的事件）；
            // 但 statusFilter 显式声明要 END（如 WMS-PIG-001 燎毛工序）时放行
            .ne(!callerWantsEnd, Pig::getCurrentStatus, PigLifecycle.END.name())
            .like(StringUtils.isNotBlank(earNoKeyword), Pig::getEarNo, earNoKeyword)
            .eq(StringUtils.isNotBlank(sexFilter), Pig::getPigSex, sexFilter)
            .eq(pigTypes.size() == 1, Pig::getPigType, pigTypes.isEmpty() ? null : pigTypes.get(0))
            .in(pigTypes.size() > 1, Pig::getPigType, pigTypes)
            .eq(barnIdFilter != null, Pig::getBarnId, barnIdFilter)
            // #23a：opt-in 排除无栋舍归属猪只，与 countByBarn 的 .isNotNull(barn_id) 口径一致
            .isNotNull(dropNullBarn, Pig::getBarnId)
            // 出栏选猪：日龄 >= minAgeDays（到龄肥猪）。{0} 占位 + apply 防注入；
            // COALESCE(birth_date, introduce_date) 与 calcAgeDays 同口径；无生日（结果 NULL）则比较非真自动剔除。
            .apply(applyMinAge, "DATEDIFF(NOW(), COALESCE(birth_date, introduce_date)) >= {0}", minAgeDays)
            .orderByDesc(Pig::getId)
            .last("LIMIT " + effectiveLimit);

        if (!statuses.isEmpty()) {
            w.in(Pig::getCurrentStatus, statuses);
        }

        List<Pig> pigs = pigMapper.selectList(w);
        if (pigs.isEmpty()) {
            return Collections.emptyList();
        }

        // D12X-MP-FARROW-WEANING-001：到期"软提示"（分娩 / 断奶选猪）。
        // 不再硬过滤——列出全部配种(PZ)/哺乳(FM)母猪，算 dueDate（预产期/到断奶期）+ due 标记，
        // 末尾按「临产排前」排序。早产能录、能浏览全部（原始需求 expectedFarrowDate 本是提示非门槛）。
        // dueType 为空 → dueDateMap 为空，所有 VO dueDate/due 为 null（向后兼容所有现有调用方）。
        Map<Long, LocalDate> dueDateMap = computeDueDateMap(pigs, dueType);

        // 批量 enrich barnCode/penCode + barnName/penName（与 queryPage 一致，避免 N+1）
        // FIX-INTRO-001 P1：同批查出 Barn/Pen 全对象，供 mp 选猪卡「位置」格显「栋舍名+栏位名」
        Set<Long> barnIds = pigs.stream().map(Pig::getBarnId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> penIds = pigs.stream().map(Pig::getPenId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Barn> barnMap = barnIds.isEmpty() ? Map.of()
            : barnMapper.selectBatchIds(barnIds).stream()
                .collect(Collectors.toMap(Barn::getId, Function.identity(), (a, b) -> a));
        Map<Long, Pen> penMap = penIds.isEmpty() ? Map.of()
            : penMapper.selectBatchIds(penIds).stream()
                .collect(Collectors.toMap(Pen::getId, Function.identity(), (a, b) -> a));

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        // row92 #1：分娩选猪（dueType=FARROW）时 enrich 最近配种日期，给 mp 分娩录入概况卡「配种日期」格用；
        // 其他 picker 场景不下发（保持 VO 轻量）。
        boolean enrichMatingDate = "FARROW".equalsIgnoreCase(dueType);
        // 「临产 / 到断奶期」badge 临近窗口（dueDate 距今 ≤ N 天 或已过期即标 due）：分娩 5 天 / 断奶 3 天。
        // 即到期日落在 [今天, 今天+N] 或更早才提醒；远未到期（差 >N 天）不打红标。
        int dueWindowDays = "WEANING".equalsIgnoreCase(dueType) ? 3 : 5;
        // 邓博 2026-06-17 #13/#19：品种 + 品系名都优先取 t_farm_breed_info 主表（客户在「品种品系表」配的权威名，
        // 如 04=国寿黑）；字典 djs_pig_breed(04=杜洛克) / djs_pig_strain 仅作回落。批量预载一次防 N+1。
        Map<String, String> breedNameMap = loadBreedStrainNameMap(1);
        Map<String, String> strainNameMap = loadBreedStrainNameMap(2);
        List<PigSearchVo> result = new ArrayList<>(pigs.size());
        for (Pig p : pigs) {
            PigSearchVo vo = new PigSearchVo();
            vo.setId(p.getId());
            vo.setEarNo(p.getEarNo());
            vo.setPigSex(p.getPigSex());
            vo.setPigType(p.getPigType());
            vo.setCurrentStatus(p.getCurrentStatus());
            // 品种/品系编码 + 中文名（mp 选猪弹层「品种/品系」标签）
            vo.setPigBreedCode(p.getPigBreedCode());
            // #19：品种 breed_info 主表优先 → 字典回落 → code 回落（与品系同源，04=国寿黑而非杜洛克）
            vo.setPigBreedName(resolveBreedStrainName(breedNameMap, "djs_pig_breed", p.getPigBreedCode()));
            vo.setPigStrainCode(p.getPigStrainCode());
            // #13：品系 breed_info 主数据优先 → 字典回落 → code 回落
            vo.setPigStrainName(resolveBreedStrainName(strainNameMap, "djs_pig_strain", p.getPigStrainCode()));
            // MP-UX-002：END 状态时携带 endReason，给 mp PigPicker 显示 "终止 · 死亡 / 上市" 用
            if (PigLifecycle.END.name().equals(p.getCurrentStatus())) {
                vo.setEndReason(p.getEndReason());
            }
            // BRD-FIX-MP-PIGSELECT-001：卡片量化字段。缺基准日期 → 该字段 null，mp 卡片该格不渲染
            vo.setAgeDays(calcAgeDays(p, today));
            vo.setParity(p.getParity());
            // FARROW 选猪卡：状态右侧天数显示「配种天数」（today - lastMatingDate），更贴近养殖「配了多少天」直觉；
            // 其余场景仍显「距上次事件天数」（today - statusStartedAt）。无配种日期时回落 statusStartedAt。
            if (enrichMatingDate && p.getLastMatingDate() != null) {
                vo.setLastEventDays(calcDaysSince(p.getLastMatingDate().atStartOfDay(), now));
                vo.setMatingDate(p.getLastMatingDate());
            }
            else {
                vo.setLastEventDays(calcDaysSince(p.getStatusStartedAt(), now));
            }
            if (p.getBarnId() != null) {
                Barn barn = barnMap.get(p.getBarnId());
                if (barn != null) {
                    vo.setBarnCode(barn.getBarnCode());
                    vo.setBarnName(barn.getBarnName());
                }
            }
            if (p.getPenId() != null) {
                Pen pen = penMap.get(p.getPenId());
                if (pen != null) {
                    vo.setPenCode(pen.getPenCode());
                    vo.setPenName(pen.getPenName());
                }
            }
            // 到期软提示：dueDate（预产期/到断奶期）+ due 标记；无基准日期 → 留 null（mp 该格不渲染）
            // 临产/临断奶阈值 = dueDate ≤ today + dueWindowDays（临近窗口内或已过期）即标 due：分娩 5 天 / 断奶 3 天。
            // 即"还有 ≤N 天到期 / 已到期"才打「到断奶期 / 临产」红标；远未到期（如还差 35 天）不标。
            LocalDate dd = dueDateMap.get(p.getId());
            if (dd != null) {
                vo.setDueDate(dd);
                vo.setDue(!dd.isAfter(today.plusDays(dueWindowDays)));
            }
            result.add(vo);
        }
        // 临产排前：due=true 优先，再按 dueDate 升序（最临近 / 超期在前），无 dueDate 的排最后。
        if (StringUtils.isNotBlank(dueType)) {
            result.sort(Comparator
                .comparingInt((PigSearchVo v) -> Boolean.TRUE.equals(v.getDue()) ? 0 : 1)
                .thenComparing(PigSearchVo::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())));
        }
        return result;
    }

    /**
     * 到期日期计算（D12X-MP-FARROW-WEANING-001 软提示，原硬过滤已废弃）：算每头母猪的预产期 / 到断奶期。
     *
     * <p>两个 dueType 共用「基准日期 + 生产周期配置天数」一套判定：</p>
     * <ul>
     *   <li>{@code FARROW}（预产期）：基准 = {@code Pig.lastMatingDate}（最近配种日，BREED 事件写入），
     *       天数 = {@code sow_breed_to_farrow_days}（配种到分娩，母猪生产配置）；{@code lastMatingDate} 为 null（无配种记录）→ 不入 map。</li>
     *   <li>{@code WEANING}（到断奶期）：基准 = 该母猪最近一条分娩日（批量一次查避免 N+1），
     *       天数 = {@code sow_farrow_to_wean_days}（分娩到断奶，母猪生产配置）；无分娩记录 → 不入 map。</li>
     * </ul>
     *
     * <p>天数从 {@code productionCycleConfigService.getValue} 读（配置驱动，禁硬编码）；配置缺失返 null
     * 时防御性记 warn 并返回空 map（退化为无 dueDate / 不排序，但仍列全部猪，不吞数据）。
     * <b>注意：不再剔除任何猪</b>——未到期 / 无基准日期的母猪照样可选（支持早产录入 + 浏览全部配种猪），
     * 仅用 dueDate + due 标记给 mp 排序「临产排前」+ badge。</p>
     *
     * @param pigs    状态白名单过滤后的候选母猪
     * @param dueType {@code FARROW} / {@code WEANING}；其他值或空 → 空 map（不计算）
     * @return {@code pigId → dueDate}（仅含有基准日期的母猪）
     */
    private Map<Long, LocalDate> computeDueDateMap(List<Pig> pigs, String dueType) {
        if (StringUtils.isBlank(dueType) || pigs.isEmpty()) {
            return Map.of();
        }

        if ("FARROW".equalsIgnoreCase(dueType)) {
            // 母猪生产配置（admin「生产配置管理 / 母猪生产配置」写入）：配种到分娩天数
            Integer gestationDays = productionCycleConfigService.getValue("sow_breed_to_farrow_days");
            if (gestationDays == null) {
                log.warn("[PigSearch] 母猪生产配置 sow_breed_to_farrow_days 缺失，dueType=FARROW 退化为无预产期提示");
                return Map.of();
            }
            return pigs.stream()
                .filter(p -> p.getId() != null && p.getLastMatingDate() != null)
                .collect(Collectors.toMap(Pig::getId, p -> p.getLastMatingDate().plusDays(gestationDays), (a, b) -> a));
        }

        if ("WEANING".equalsIgnoreCase(dueType)) {
            // 母猪生产配置（admin「生产配置管理 / 母猪生产配置」写入）：分娩到断奶天数
            Integer lactationDays = productionCycleConfigService.getValue("sow_farrow_to_wean_days");
            if (lactationDays == null) {
                log.warn("[PigSearch] 母猪生产配置 sow_farrow_to_wean_days 缺失，dueType=WEANING 退化为无到断奶期提示");
                return Map.of();
            }
            Set<Long> pigIds = pigs.stream().map(Pig::getId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (pigIds.isEmpty()) {
                return Map.of();
            }
            // 批量一次查每头母猪最近分娩日（避免逐头 N+1）
            Map<Long, LocalDate> lastFarrowMap = pigMapper.selectLastFarrowDateByPigIds(pigIds).stream()
                .filter(v -> v.getPigId() != null && v.getLastFarrowDate() != null)
                .collect(Collectors.toMap(PigLastFarrowVo::getPigId, PigLastFarrowVo::getLastFarrowDate, (a, b) -> a));
            Map<Long, LocalDate> dueMap = new HashMap<>(lastFarrowMap.size());
            lastFarrowMap.forEach((pigId, lastFarrow) -> dueMap.put(pigId, lastFarrow.plusDays(lactationDays)));
            return dueMap;
        }

        // 未知 dueType：不计算
        return Map.of();
    }

    @Override
    public List<PigBarnCountVo> countByBarn(String statusFilter, String sexFilter, String pigTypeFilter) {
        List<String> statuses = parseStatusFilter(statusFilter);
        boolean callerWantsEnd = statuses.contains(PigLifecycle.END.name());

        // pigTypeFilter 支持 CSV（如 'piglet,fattening' 给阉割选猪 = 公的仔猪+育肥猪）；单值退化 .eq，CSV 走 IN。
        // 与 searchByEarKeyword 同口径，否则栋舍 chip 计数与列表条数对不上。
        List<String> pigTypes = StringUtils.isNotBlank(pigTypeFilter)
            ? Arrays.stream(pigTypeFilter.split(",")).map(String::trim).filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList())
            : Collections.emptyList();

        LambdaQueryWrapper<Pig> w = new LambdaQueryWrapper<Pig>()
            .ne(!callerWantsEnd, Pig::getCurrentStatus, PigLifecycle.END.name())
            .eq(StringUtils.isNotBlank(sexFilter), Pig::getPigSex, sexFilter)
            .eq(pigTypes.size() == 1, Pig::getPigType, pigTypes.isEmpty() ? null : pigTypes.get(0))
            .in(pigTypes.size() > 1, Pig::getPigType, pigTypes)
            // 无栋舍归属的猪只不计入任何 chip
            .isNotNull(Pig::getBarnId);
        if (!statuses.isEmpty()) {
            w.in(Pig::getCurrentStatus, statuses);
        }

        List<Pig> pigs = pigMapper.selectList(w);
        if (pigs.isEmpty()) {
            return Collections.emptyList();
        }

        // barnId → count
        Map<Long, Long> countByBarnId = pigs.stream()
            .collect(Collectors.groupingBy(Pig::getBarnId, Collectors.counting()));

        // enrich barnCode/barnName（一次 batch 查）
        Map<Long, Barn> barnMap = barnMapper.selectBatchIds(countByBarnId.keySet()).stream()
            .collect(Collectors.toMap(Barn::getId, Function.identity(), (a, b) -> a));

        return countByBarnId.entrySet().stream()
            .map(e -> {
                Barn barn = barnMap.get(e.getKey());
                if (barn == null) {
                    return null;
                }
                PigBarnCountVo vo = new PigBarnCountVo();
                vo.setBarnCode(barn.getBarnCode());
                vo.setBarnName(barn.getBarnName());
                vo.setCount(e.getValue().intValue());
                return vo;
            })
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(PigBarnCountVo::getBarnCode, Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
    }

    /** barnCode 业务码 → barnId；查不到返 null。 */
    private Long resolveBarnIdByCode(String barnCode) {
        Barn barn = barnMapper.selectOne(
            new LambdaQueryWrapper<Barn>()
                .eq(Barn::getBarnCode, barnCode)
                .last("LIMIT 1"));
        return barn == null ? null : barn.getId();
    }

    /**
     * 日龄 = NOW - birthDate（缺 birthDate 时 fallback NOW - introduceDate）；两者均空 → null。
     */
    private Integer calcAgeDays(Pig p, LocalDate today) {
        LocalDate base = p.getBirthDate() != null ? p.getBirthDate() : p.getIntroduceDate();
        if (base == null) {
            return null;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(base, today);
        return (int) Math.max(days, 0L);
    }

    /** 距某时刻天数 = NOW - since；since 为 null → null。 */
    private Integer calcDaysSince(LocalDateTime since, LocalDateTime now) {
        if (since == null) {
            return null;
        }
        long days = Duration.between(since, now).toDays();
        return (int) Math.max(days, 0L);
    }

    private int clampLimit(Integer raw) {
        if (raw == null || raw <= 0) {
            return 20;
        }
        return Math.min(raw, 100);
    }

    private List<String> parseStatusFilter(String csv) {
        if (StringUtils.isBlank(csv)) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            // 防御性：未知 lifecycle code 静默丢弃（避免 SQL 注入风险及拼写错误返空但报 500）
            .filter(s -> {
                try {
                    PigLifecycle.valueOf(s);
                    return true;
                } catch (IllegalArgumentException ignore) {
                    return false;
                }
            })
            .collect(Collectors.toList());
    }

    @Override
    public TableDataInfo<PigStatusRecordVo> queryStatusRecordPage(PigStatusRecordQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PigStatusRecord> w = new LambdaQueryWrapper<PigStatusRecord>()
            .eq(query.getPigId() != null, PigStatusRecord::getPigId, query.getPigId())
            .like(StringUtils.isNotBlank(query.getEarNo()), PigStatusRecord::getEarNo, query.getEarNo())
            .eq(StringUtils.isNotBlank(query.getEventType()), PigStatusRecord::getEventType, query.getEventType())
            .eq(StringUtils.isNotBlank(query.getNewStatus()), PigStatusRecord::getNewStatus, query.getNewStatus())
            // 变更人模糊：按 sys_user.nick_name LIKE 命中的 user_id 反查 create_by。
            // 用 {0} 占位 + apply 防注入；create_by 即变更人（记录由触发事件的用户写入）。
            .apply(StringUtils.isNotBlank(query.getCreateByName()),
                "create_by IN (SELECT user_id FROM sys_user WHERE nick_name LIKE CONCAT('%', {0}, '%'))",
                query.getCreateByName())
            .ge(query.getChangeTimeStart() != null, PigStatusRecord::getChangeTime, query.getChangeTimeStart())
            .le(query.getChangeTimeEnd() != null, PigStatusRecord::getChangeTime, query.getChangeTimeEnd())
            .orderByDesc(PigStatusRecord::getChangeTime, PigStatusRecord::getId);
        Page<PigStatusRecordVo> page = statusRecordMapper.selectVoPage(pageQuery.build(), w);
        return TableDataInfo.build(page);
    }

    /**
     * 事件副作用：胎次 / 配种关联 / 转栏 / 终止原因等。
     * 不在状态机内做以保持状态机为纯函数。
     */
    private void applyEventSideEffects(Pig pig, PigEventBo bo, PigLifecycle to) {
        PigStatusEvent ev = bo.getEventType();

        if (ev == FARROW) {
            pig.setParity(Optional.ofNullable(pig.getParity()).orElse(0) + 1);
        }
        if (ev == BREED) {
            pig.setMatingId(bo.getRelatedEventId());
        }
        if (ev == TRANSFER) {
            Map<String, Object> payload = bo.getPayload();
            if (payload != null) {
                Object newBarn = payload.get("newBarnId");
                Object newPen = payload.get("newPenId");
                Object newPigType = payload.get("newPigType");
                if (newBarn != null) {
                    pig.setBarnId(parseLong(newBarn, "newBarnId"));
                }
                if (newPen != null) {
                    pig.setPenId(parseLong(newPen, "newPenId"));
                }
                // D5 audit a-2：piglet 转育肥舍时 service 端将 payload.newPigType='fattening' 写入 pig.pig_type，
                // 否则 BRD-DASH-001 育肥猪存栏统计漏算。
                if (newPigType instanceof CharSequence cs && cs.length() > 0) {
                    pig.setPigType(cs.toString());
                }
            }
        }
        if (to == PigLifecycle.END) {
            pig.setEndReason(switch (ev) {
                case DIE -> PigEndReason.DEAD.name();
                case ELIMINATE -> PigEndReason.CULL.name();
                case SLAUGHTER -> PigEndReason.MARKET.name();
                default -> throw new IllegalStateException("END but event=" + ev);
            });
        }
        // 防御：消费 ELIMINATE / DIE / SLAUGHTER 后由 switch case 兜底，import 仅用于静态导入
        if (ev == DIE || ev == ELIMINATE || ev == SLAUGHTER) {
            // 无额外副作用，保留 import / static check 兼容
            log.debug("[BRD-CORE-001] terminal event {} pigId={} end_reason={}", ev, pig.getId(), pig.getEndReason());
        }
    }

    /**
     * BREED 后置 wrapper-only update：mating_count += 1, last_mating_date = payload.breedingDate（缺时 = today）。
     *
     * <p>独立于主 {@code updateById(pig)} 的原因：</p>
     * <ul>
     *   <li>主 update 走 {@code @Version} 乐观锁，{@code mating_count += 1} 用 setSql 原子加法，避免 race；</li>
     *   <li>{@code last_mating_date} 为业务衍生值，与状态机推进解耦；</li>
     *   <li>本方法在 fireEvent 事务内执行，与状态记录 + pig 主更新同生共死。</li>
     * </ul>
     */
    private void applyBreedingCounters(Long pigId, PigEventBo bo) {
        LocalDate breedingDate = extractBreedingDate(bo);
        pigMapper.update(null,
            Wrappers.<Pig>update()
                .eq("id", pigId)
                .setSql("mating_count = COALESCE(mating_count, 0) + 1")
                .set("last_mating_date", breedingDate));
    }

    /**
     * 从 payload 取 breedingDate；优先 payload."breedingDate"（LocalDate / LocalDateTime / String "yyyy-MM-dd"），
     * 其次 bo.eventAt.toLocalDate()，最后 LocalDate.now()。
     */
    private LocalDate extractBreedingDate(PigEventBo bo) {
        Map<String, Object> payload = bo.getPayload();
        if (payload != null) {
            Object raw = payload.get("breedingDate");
            if (raw instanceof LocalDate ld) {
                return ld;
            }
            if (raw instanceof LocalDateTime ldt) {
                return ldt.toLocalDate();
            }
            if (raw instanceof CharSequence cs && cs.length() > 0) {
                try {
                    return LocalDate.parse(cs.toString());
                } catch (RuntimeException ignore) {
                    // fall through to eventAt
                }
            }
        }
        if (bo.getEventAt() != null) {
            return bo.getEventAt().toLocalDate();
        }
        return LocalDate.now();
    }

    private LambdaQueryWrapper<Pig> buildWrapper(PigQuery query) {
        LambdaQueryWrapper<Pig> w = new LambdaQueryWrapper<>();
        if (query == null) {
            return w.orderByDesc(Pig::getId);
        }
        w.like(StringUtils.isNotBlank(query.getEarNo()), Pig::getEarNo, query.getEarNo())
            .eq(StringUtils.isNotBlank(query.getPigSex()), Pig::getPigSex, query.getPigSex())
            .eq(StringUtils.isNotBlank(query.getPigType()), Pig::getPigType, query.getPigType())
            .eq(StringUtils.isNotBlank(query.getCurrentStatus()), Pig::getCurrentStatus, query.getCurrentStatus())
            .eq(query.getBarnId() != null, Pig::getBarnId, query.getBarnId())
            .eq(query.getPenId() != null, Pig::getPenId, query.getPenId())
            .ne(Boolean.TRUE.equals(query.getExcludeEnd()), Pig::getCurrentStatus, PigLifecycle.END.name());

        // 栋舍/栏位按名称模糊：先反查命中的 barn_id / pen_id 集合，再 in(...)；
        // 命中空集合时强制返空（in 空集合在 MP 里被忽略 → 改用恒假条件），避免误返全量。
        if (StringUtils.isNotBlank(query.getBarnName())) {
            Set<Long> barnIds = resolveBarnIdsByName(query.getBarnName());
            if (barnIds.isEmpty()) {
                w.apply("1 = 0");
            } else {
                w.in(Pig::getBarnId, barnIds);
            }
        }
        if (StringUtils.isNotBlank(query.getPenName())) {
            Set<Long> penIds = resolvePenIdsByName(query.getPenName());
            if (penIds.isEmpty()) {
                w.apply("1 = 0");
            } else {
                w.in(Pig::getPenId, penIds);
            }
        }

        w.orderByDesc(Pig::getId);
        return w;
    }

    /** 栋舍名称模糊 → barn_id 集合（多匹配）；无命中返空集合。 */
    private Set<Long> resolveBarnIdsByName(String barnName) {
        return barnMapper.selectList(
                new LambdaQueryWrapper<Barn>()
                    .select(Barn::getId)
                    .like(Barn::getBarnName, barnName))
            .stream().map(Barn::getId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /** 栏位名称模糊 → pen_id 集合（多匹配）；无命中返空集合。 */
    private Set<Long> resolvePenIdsByName(String penName) {
        return penMapper.selectList(
                new LambdaQueryWrapper<Pen>()
                    .select(Pen::getId)
                    .like(Pen::getPenName, penName))
            .stream().map(Pen::getId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private PigLifecycle parseLifecycle(String name, Long pigId) {
        if (StringUtils.isBlank(name)) {
            throw new ServiceException(I18nMessages.t("pig.state.invalid", "null", pigId));
        }
        try {
            return PigLifecycle.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(I18nMessages.t("pig.state.invalid", name, pigId));
        }
    }

    private Integer calcDurationDays(LocalDateTime statusStartedAt, LocalDateTime eventAt) {
        if (statusStartedAt == null || eventAt == null) {
            return null;
        }
        long days = Duration.between(statusStartedAt, eventAt).toDays();
        return (int) Math.max(days, 0L);
    }

    private Long parseLong(Object raw, String field) {
        try {
            return raw instanceof Number n ? n.longValue() : Long.parseLong(raw.toString());
        } catch (NumberFormatException e) {
            throw new ServiceException(I18nMessages.t("pig.event.payload_invalid", field, raw.toString()));
        }
    }

    /**
     * 手工映射 {@link PigStatusRecord} → {@link PigStatusRecordVo}。
     * 不走 {@code MapstructUtils} 是为了单测无 Spring 上下文也能跑（{@code MapstructUtils}
     * static init 依赖 {@code SpringUtil.getBean}）。生产路径手工 copy 与 MapStruct 效果等价。
     */
    private PigStatusRecordVo toRecordVo(PigStatusRecord r) {
        if (r == null) {
            return null;
        }
        PigStatusRecordVo vo = new PigStatusRecordVo();
        vo.setId(r.getId());
        vo.setPigId(r.getPigId());
        vo.setEarNo(r.getEarNo());
        vo.setOldStatus(r.getOldStatus());
        vo.setNewStatus(r.getNewStatus());
        vo.setEventType(r.getEventType());
        vo.setRelatedEventId(r.getRelatedEventId());
        vo.setDurationDays(r.getDurationDays());
        vo.setChangeTime(r.getChangeTime());
        vo.setCreateTime(r.getCreateTime());
        vo.setCreateBy(r.getCreateBy());
        return vo;
    }

    /** 手工映射 {@link PigVo} → {@link PigDetailVo}（同上原因）。 */
    private PigDetailVo toDetailVo(PigVo src) {
        PigDetailVo d = new PigDetailVo();
        if (src == null) {
            return d;
        }
        d.setId(src.getId());
        d.setEarNo(src.getEarNo());
        d.setEarTag(src.getEarTag());
        d.setLifecycleId(src.getLifecycleId());
        d.setRecyclable(src.getRecyclable());
        d.setPigSex(src.getPigSex());
        d.setPigType(src.getPigType());
        d.setPigBreedCode(src.getPigBreedCode());
        d.setPigBreedName(src.getPigBreedName());
        d.setPigStrainCode(src.getPigStrainCode());
        d.setPigStrainName(src.getPigStrainName());
        d.setCurrentStatus(src.getCurrentStatus());
        d.setStatusStartedAt(src.getStatusStartedAt());
        d.setEndReason(src.getEndReason());
        d.setBirthDate(src.getBirthDate());
        d.setBirthWeight(src.getBirthWeight());
        d.setIntroduceDate(src.getIntroduceDate());
        d.setParity(src.getParity());
        d.setBarnId(src.getBarnId());
        d.setBarnCode(src.getBarnCode());
        d.setBarnName(src.getBarnName());
        d.setPenId(src.getPenId());
        d.setPenCode(src.getPenCode());
        d.setPenName(src.getPenName());
        d.setMatingId(src.getMatingId());
        d.setMotherEar(src.getMotherEar());
        d.setMotherEarTag(src.getMotherEarTag());
        d.setFatherEar(src.getFatherEar());
        d.setFatherEarTag(src.getFatherEarTag());
        d.setMatingCount(src.getMatingCount());
        d.setLastMatingDate(src.getLastMatingDate());
        d.setRemark(src.getRemark());
        d.setCreateTime(src.getCreateTime());
        d.setCreateBy(src.getCreateBy());
        d.setCreateName(src.getCreateName());
        d.setVersion(src.getVersion());
        return d;
    }
}
