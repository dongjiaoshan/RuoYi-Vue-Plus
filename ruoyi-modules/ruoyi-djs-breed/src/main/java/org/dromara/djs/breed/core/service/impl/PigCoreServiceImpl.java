package org.dromara.djs.breed.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.service.OssService;
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
import org.dromara.djs.breed.core.util.PigAgeUtil;
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
import org.dromara.djs.breed.farm.service.PenCountUpdater;
import org.dromara.djs.breed.event.growth.domain.PigGrowth;
import org.dromara.djs.breed.event.growth.mapper.PigGrowthMapper;
import org.dromara.djs.breed.event.intro.domain.PigIntroduce;
import org.dromara.djs.breed.event.intro.mapper.PigIntroduceMapper;
import org.dromara.djs.breed.production.service.IProductionCycleConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import static org.dromara.djs.breed.core.enums.PigStatusEvent.TO_FATTEN;
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
    /**
     * 预产期展示固定天数：配种日 + 114（猪妊娠期恒定，Kevin 2026-07-05）。
     * <p>展示用的预产期/断奶期固定 114/25，不随 admin 生产周期配置漂移；生产周期配置仅用于板块「筛选」（哪些母猪进分娩/断奶列表）。</p>
     */
    private static final int GESTATION_DAYS_DISPLAY = 114;
    /** 断奶期展示固定天数：分娩日 + 25。 */
    private static final int LACTATION_DAYS_DISPLAY = 25;

    private final PigMapper pigMapper;
    private final PigStatusRecordMapper statusRecordMapper;
    private final PigStateMachine stateMachine;
    private final ApplicationEventPublisher eventPublisher;
    private final BarnMapper barnMapper;
    private final PenMapper penMapper;
    private final DictService dictService;
    private final IProductionCycleConfigService productionCycleConfigService;
    private final BreedInfoMapper breedInfoMapper;
    private final PenCountUpdater penCountUpdater;

    /**
     * 生长记录 mapper（详情顶卡主图回退 + search「上次测量」enrich 用）。字段注入而非构造注入：保持
     * {@link PigCoreServiceImpl} 构造器签名精简（{@code PigCoreServiceImplTest} 直接 {@code new} 构造，
     * 改签名会连带改单测）；本字段仅 {@link #queryDetail} / {@link #searchByEarKeyword} 用，单测不覆盖该路径，注入与否不影响既有用例。
     */
    @Autowired
    private PigGrowthMapper pigGrowthMapper;

    /**
     * OSS 解析服务（详情顶卡生长照片 ossId → 可访问 URL）。字段注入理由同 {@link #pigGrowthMapper}：
     * 保持构造器签名不变；mp {@code <image>} 带不了 Bearer token，必须后端把 ossId 预解析成 URL。
     */
    @Autowired
    private OssService ossService;

    /**
     * 引种登记 mapper（详情顶卡猪只照片取引种凭证图）。字段注入理由同 {@link #pigGrowthMapper}：
     * 保持 9 参构造器签名不变（{@code PigCoreServiceImplTest} 直接 new 构造）；仅 {@link #queryDetail} 用，单测不覆盖该路径。
     */
    @Autowired
    private PigIntroduceMapper pigIntroduceMapper;

    public PigCoreServiceImpl(PigMapper pigMapper,
                              PigStatusRecordMapper statusRecordMapper,
                              PigStateMachine stateMachine,
                              ApplicationEventPublisher eventPublisher,
                              BarnMapper barnMapper,
                              PenMapper penMapper,
                              DictService dictService,
                              IProductionCycleConfigService productionCycleConfigService,
                              BreedInfoMapper breedInfoMapper,
                              PenCountUpdater penCountUpdater) {
        this.pigMapper = pigMapper;
        this.statusRecordMapper = statusRecordMapper;
        this.stateMachine = stateMachine;
        this.eventPublisher = eventPublisher;
        this.barnMapper = barnMapper;
        this.penMapper = penMapper;
        this.dictService = dictService;
        this.productionCycleConfigService = productionCycleConfigService;
        this.breedInfoMapper = breedInfoMapper;
        this.penCountUpdater = penCountUpdater;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigStatusRecordVo fireEvent(PigEventBo bo) {
        Pig pig = requireEventPig(bo);

        // from 可为 null：育肥猪 / 仔猪 / 公猪等非种母猪类型空状态（''/NULL），仍可终止 / 转栏 / 阉割。
        PigLifecycle from = resolveFrom(pig);
        PigLifecycle to = stateMachine.nextStatus(from, bo.getEventType(), pig.getPigSex(), bo.getPayload());

        LocalDateTime eventAt = Optional.ofNullable(bo.getEventAt()).orElseGet(LocalDateTime::now);

        // 事件前所在栏位：applyEventSideEffects 会就地改写 pig.penId（TRANSFER），迁栏计数要用旧值
        Long penIdBefore = pig.getPenId();

        // 1. 写 status_record
        PigStatusRecord record = new PigStatusRecord();
        record.setPigId(pig.getId());
        record.setEarNo(pig.getEarNo());
        // new_status NOT NULL：空状态（非种母猪）写 ''（非 null）。old_status 可空，空前态写 null（与 createPig/internalIntro 一致）。
        record.setOldStatus(from == null ? null : from.name());
        record.setNewStatus(to == null ? "" : to.name());
        record.setEventType(bo.getEventType().name());
        record.setRelatedEventId(bo.getRelatedEventId());
        record.setChangeTime(withOperateClock(eventAt));
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

        // 2c. 栏位在场头数同步（FIX-BRD-PENCOUNT-001）：转群旧栏 -1 / 新栏 +1；终止事件（DIE /
        //     ELIMINATE / SLAUGHTER → END）所在栏 -1。放在主 update 成功之后，乐观锁冲突已抛异常，
        //     不会出现「猪没动而计数动了」。END 猪再 fireEvent 会被状态机拒（终态），故每头最多减一次。
        if (bo.getEventType() == TRANSFER || bo.getEventType() == TO_FATTEN) {
            penCountUpdater.move(penIdBefore, pig.getPenId(), 1);
        } else if (to == PigLifecycle.END) {
            penCountUpdater.decrease(pig.getPenId(), 1);
        }

        // 3. 发布 Spring event（下游 dashboard / 推送 / 燎毛各自订阅）
        eventPublisher.publishEvent(new PigStateChangedEvent(this, record, pig, from, to));

        log.info("[BRD-CORE-001] fireEvent pigId={} earNo={} event={} {} -> {} relatedEventId={}",
            pig.getId(), pig.getEarNo(), bo.getEventType(), from, to, bo.getRelatedEventId());

        return toRecordVo(record);
    }

    @Override
    public PigLifecycle precheckEvent(PigEventBo bo) {
        Pig pig = requireEventPig(bo);
        // 与 fireEvent 走同一个 nextStatus，只是丢掉结果不落库：guard 不通过在这里就抛 400。
        return stateMachine.nextStatus(resolveFrom(pig), bo.getEventType(), pig.getPigSex(), bo.getPayload());
    }

    /** 事件入参基础校验 + 取猪只（{@link #fireEvent} / {@link #precheckEvent} 共用）。 */
    private Pig requireEventPig(PigEventBo bo) {
        Objects.requireNonNull(bo, "PigEventBo must not be null");
        if (bo.getPigId() == null || bo.getEventType() == null) {
            throw new ServiceException(I18nMessages.t("pig.event.bo_required"));
        }
        Pig pig = pigMapper.selectById(bo.getPigId());
        if (pig == null) {
            throw new ServiceException(I18nMessages.t("pig.not_found", bo.getPigId()));
        }
        return pig;
    }

    /**
     * 当前状态解析：空状态（非种母猪 {@code ''}/NULL）→ {@code null}（仍可终止 / 转栏 / 阉割）；
     * 非法状态串 → ServiceException。
     */
    private PigLifecycle resolveFrom(Pig pig) {
        String curStatus = pig.getCurrentStatus();
        return StringUtils.isBlank(curStatus) ? null : parseLifecycle(curStatus, pig.getId());
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
        // 进入初始状态（后备 HB）的时间 = 引种日期当天，而非提交时间：
        // 外部引种允许补录（引种日期可选历史日），进后备的业务时点应按页面选的引种日期，
        // 否则「后备-配种天数」阈值（filterBreedReady）会从提交日起算、少算在场天数。
        // 取 atStartOfDay 走 date 级口径（与 calcDaysSince 的按日差一致）；无引种日期回落 now。
        LocalDateTime statusStartedAt = bo.getIntroduceDate() != null ? bo.getIntroduceDate().atStartOfDay() : now;

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
        pig.setStatusStartedAt(statusStartedAt);
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
        // INTRO 事件的业务发生时点 = 引种日期（与 status_started_at 同源），养殖记录里「进入后备」按引种日期显示；
        // 引种日期就是当天时补上真实操作时刻（日期不变，只补时分秒，见 withOperateClock）
        record.setChangeTime(withOperateClock(statusStartedAt));
        statusRecordMapper.insert(record);

        eventPublisher.publishEvent(new PigStateChangedEvent(this, record, pig, null, initial));

        log.info("[BRD-CORE-001] createPig pigId={} earNo={} initial={} pigType={}",
            pig.getId(), pig.getEarNo(), initial, pig.getPigType());

        return pig;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void internalIntroToReserve(Long pigId, LocalDate introduceDate) {
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
        LocalDate effectiveIntroduceDate = introduceDate != null ? introduceDate : now.toLocalDate();
        // 进后备（HB）的业务时点 = 内部引种日期当天，而非提交时间（同外部引种口径，见 createPig）；
        // 无引种日期回落 now。durationDays 仍按 now 结算（原态在场天数到"提交留种"这一刻为止）。
        LocalDateTime statusStartedAt = effectiveIntroduceDate.atStartOfDay();
        PigStatusRecord record = new PigStatusRecord();
        record.setPigId(pig.getId());
        record.setEarNo(pig.getEarNo());
        record.setOldStatus(oldStatus);
        record.setNewStatus(newStatus);
        record.setEventType(PigStatusEvent.INTRO.name());
        record.setChangeTime(withOperateClock(statusStartedAt));
        if (oldStatus != null) {
            record.setDurationDays(calcDurationDays(pig.getStatusStartedAt(), now));
        }
        statusRecordMapper.insert(record);

        pig.setPigType(newType);
        pig.setCurrentStatus(newStatus);
        pig.setStatusStartedAt(statusStartedAt);
        // 内部引种复用场内既有猪只，不经过 createPig；引种业务日期必须同步回猪只主表，
        // 否则详情页读取 t_farm_pig_info.introduce_date 时只能显示空值。
        pig.setIntroduceDate(effectiveIntroduceDate);
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
        // row43：状态记录只呈现真实的生命周期状态变更；过滤空转记录（old_status = new_status，
        // 典型为栋舍转移 TRANSFER 在同一状态内写的记录），否则会在「状态记录」里出现相邻两条同状态（如断奶→断奶）。
        // 转移本身在「转移记录」tab 呈现，不属于状态变更。old_status 为 null（引种首条）保留。
        LambdaQueryWrapper<PigStatusRecord> w = new LambdaQueryWrapper<PigStatusRecord>()
            .eq(PigStatusRecord::getPigId, pigId)
            .apply("(old_status IS NULL OR old_status <> new_status)")
            .orderByDesc(PigStatusRecord::getChangeTime, PigStatusRecord::getId)
            .last("LIMIT " + RECENT_HISTORY_LIMIT);
        detail.setRecentHistory(statusRecordMapper.selectVoList(w));
        // R160：猪只照片顶卡主图取引种登记凭证图（mp `image` 优先，无则回退生长记录图）。
        detail.setImage(resolveIntroPhotoUrl(pigId, detail.getEarNo()));
        // row61：字段名沿用 growthPhotoOssId（FE 已按 <image src> 消费），但存的是 OSS 解析后的**可访问 URL**
        // （非裸 ossId）；mp <image> 带不了 Bearer token，必须后端预解析。无生长记录 / 无照片 → null。
        detail.setGrowthPhotoOssId(resolveLatestGrowthPhotoUrl(pigId));
        return detail;
    }

    /**
     * 该猪最新生长记录主图「可访问 URL」（row61）：{@code t_farm_pig_growth} 按 measure_date DESC（同日按 id DESC）
     * 取首条，取其 {@code photo_oss_ids}（逗号分隔多图）第一张 ossId，再经 {@link OssService#selectUrlByIds}
     * 解析成可直接给 mp {@code <image>} 渲染的 URL。无生长记录 / 该记录无照片 / 解析失败 → null。
     */
    private String resolveLatestGrowthPhotoUrl(Long pigId) {
        PigGrowth latest = pigGrowthMapper.selectOne(new LambdaQueryWrapper<PigGrowth>()
            .eq(PigGrowth::getPigId, pigId)
            .orderByDesc(PigGrowth::getMeasureDate, PigGrowth::getId)
            .last("LIMIT 1"));
        if (latest == null || StringUtils.isBlank(latest.getPhotoOssIds())) {
            return null;
        }
        String firstOssId = latest.getPhotoOssIds().split(",")[0].trim();
        if (StringUtils.isBlank(firstOssId)) {
            return null;
        }
        String url = ossService.selectUrlByIds(firstOssId);
        if (StringUtils.isBlank(url)) {
            return null;
        }
        // selectUrlByIds 多 id 时逗号拼接；单 id 取首段即可，trim 去空白
        String first = url.split(",")[0].trim();
        return StringUtils.isNotBlank(first) ? first : null;
    }

    /**
     * 猪只照片「可访问 URL」（R160）：取引种登记凭证图首张。
     * <p>引种记录反查：内部引种 {@code pig_id} 直接命中；外部引种 {@code pig_id} 空、按 {@code start_ear_no} 命中该批首头。
     * 取最近一条带凭证图的引种记录 {@code proof_oss_ids} 第一张 ossId → URL；无引种记录 / 无凭证图 / 解析失败 → null
     * （前端回退生长记录图 {@code growthPhotoOssId}）。</p>
     */
    private String resolveIntroPhotoUrl(Long pigId, String earNo) {
        if (pigId == null && StringUtils.isBlank(earNo)) {
            return null;
        }
        PigIntroduce intro = pigIntroduceMapper.selectOne(new LambdaQueryWrapper<PigIntroduce>()
            .and(q -> q.eq(pigId != null, PigIntroduce::getPigId, pigId)
                .or(StringUtils.isNotBlank(earNo), o -> o.eq(PigIntroduce::getStartEarNo, earNo)))
            .isNotNull(PigIntroduce::getProofOssIds)
            .ne(PigIntroduce::getProofOssIds, "")
            .orderByDesc(PigIntroduce::getId)
            .last("LIMIT 1"));
        if (intro == null || StringUtils.isBlank(intro.getProofOssIds())) {
            return null;
        }
        String firstOssId = intro.getProofOssIds().split(",")[0].trim();
        if (StringUtils.isBlank(firstOssId)) {
            return null;
        }
        String url = ossService.selectUrlByIds(firstOssId);
        if (StringUtils.isBlank(url)) {
            return null;
        }
        String first = url.split(",")[0].trim();
        return StringUtils.isNotBlank(first) ? first : null;
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
        vo.setPigBreedLabel(resolveBreedStrainName(loadBreedStrainNameMap(1), pig.getPigBreedCode()));
        vo.setPigStrainCode(pig.getPigStrainCode());
        vo.setPigStrainLabel(resolveBreedStrainName(loadBreedStrainNameMap(2), pig.getPigStrainCode()));
        vo.setAgeDays(calcAgeDays(pig, LocalDate.now()));
        // 当前位置：栋舍名 + 栏位名拼接（缺任一则降级，全缺为 null）
        String barnName = pig.getBarnId() != null
            ? Optional.ofNullable(barnMapper.selectById(pig.getBarnId())).map(Barn::getBarnName).orElse(null) : null;
        String penName = pig.getPenId() != null
            ? Optional.ofNullable(penMapper.selectById(pig.getPenId())).map(Pen::getPenName).orElse(null) : null;
        vo.setBarnName(barnName);
        vo.setPenName(penName);
        if (StringUtils.isNotBlank(penName)) {
            // pen_name 只存栏位名（如「散栏01」，admin row164），与 barnName 拼成「育肥舍1栋散栏01」；
            // 早期存量若已含栋舍名前缀则不再重复拼
            vo.setCurrentLocation(StringUtils.isNotBlank(barnName) && !penName.startsWith(barnName)
                ? barnName + penName : penName);
        }
        else if (StringUtils.isNotBlank(barnName)) {
            vo.setCurrentLocation(barnName);
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
     * 缺则回落原始 code（旧静态字典 djs_pig_breed/djs_pig_strain 已废除）。
     */
    @Override
    public String resolveBreedStrainName(Map<String, String> infoNameMap, String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String name = infoNameMap.get(code);
        return StringUtils.isNotBlank(name) ? name : code;
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
        // 回填日龄（今天 - birth_date，缺则 introduce_date），供 mp 猪只卡片显示
        LocalDate today = LocalDate.now();
        for (PigVo vo : page.getRecords()) {
            vo.setAgeDays(PigAgeUtil.ageDaysAt(vo.getBirthDate(), vo.getIntroduceDate(), today));
        }
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
            vo.setPigBreedName(resolveBreedStrainName(breedNameMap, vo.getPigBreedCode()));
            vo.setPigStrainName(resolveBreedStrainName(strainNameMap, vo.getPigStrainCode()));
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
        // row43：状态记录只呈现真实的生命周期状态变更；过滤空转记录（old_status = new_status，
        // 典型为栋舍转移 TRANSFER 在同一状态内写的记录），否则「状态记录」里会出现相邻两条同状态（如断奶→断奶）。
        // 转移本身在「转移记录」tab 呈现，不属于状态变更。old_status 为 null（引种首条）保留。
        LambdaQueryWrapper<PigStatusRecord> w = new LambdaQueryWrapper<PigStatusRecord>()
            .eq(PigStatusRecord::getPigId, pigId)
            .apply("(old_status IS NULL OR old_status <> new_status)")
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
                                                Integer minAgeDays,
                                                Integer isCastrated,
                                                Boolean breedReady) {
        int effectiveLimit = clampLimit(limit);
        // 小程序 row256-259：**输入耳号搜索时放行「默认待办窗口」类过滤**。
        // dueType(到产期/到断奶期) / minAgeDays(到出栏日龄) / breedReady(够在场天数) / excludeNullBarn
        // 这四个维度存在的意义是「进页默认只列今天该干的那批猪」，属于列表的默认收窄，不是业务硬约束。
        // 工人一旦手输耳号，就是奔着某一头具体的猪去的，此时还叠这些窗口 = 「库里明明有这头猪却搜不出来」
        // （分娩录入搜不到未到产期的配种母猪、断奶录入搜不到未到期的分娩母猪、出栏录入搜不到未到龄育肥猪、
        //  配种录入搜不到刚断奶的母猪）。搜索态只保留真·业务硬约束：状态白名单 statusFilter + 猪类
        // pigTypeFilter + 性别 sexFilter + 阉割态 isCastrated + 栋舍 barnCode（用户显式点选的可见筛选）。
        // 注意 dueType 本身不置空：它同时驱动预产期/到断奶期 enrich、临产角标与排序，置空会让搜索结果丢角标；
        // 这里只跳过它的**硬筛**分支（见下方 applyDueFilter）。
        boolean searchingByEarNo = StringUtils.isNotBlank(earNoKeyword);
        // 出栏选猪：仅返回到龄肥猪（日龄 >= slaughter_age_days）。日龄 = NOW − birth_date（缺则 introduce_date）；
        // 两者均空 → DATEDIFF 为 NULL，比较结果非真 → 自动剔除（无生日的猪不算到龄）。minAgeDays ≤0/null 不过滤。
        boolean applyMinAge = !searchingByEarNo && minAgeDays != null && minAgeDays > 0;
        // FIX-BREEDING-001 #23a：配种选猪场景传 excludeNullBarn=true，排除无栋舍归属猪只，
        // 与 countByBarn（barn-count chip）口径对齐（列表数 = 各栋舍 chip 之和）。默认 false 不变。
        boolean dropNullBarn = !searchingByEarNo && Boolean.TRUE.equals(excludeNullBarn);
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
            // FIX-BRD-CASTRATE-ISCASTRATED-001：阉割选猪传 isCastrated=1（仅未阉割猪可选）；null → 不过滤。
            .eq(isCastrated != null, Pig::getIsCastrated, isCastrated)
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

        // row13：配种选猪场景（breedReady=true）按后台「母猪生产配置」最小在场天数过滤——
        // 未达对应天数（断奶/返情/空怀/流产→配种）的母猪不进待配种列表；后备 HB 无阈值全显。
        // row256：搜耳号时放行（见上方 searchingByEarNo）。
        if (!searchingByEarNo && Boolean.TRUE.equals(breedReady)) {
            pigs = filterBreedReady(pigs, LocalDateTime.now());
            if (pigs.isEmpty()) {
                return Collections.emptyList();
            }
        }

        // dueType 为空 → dueDateMap 为空，所有 VO dueDate/due 为 null（向后兼容所有现有调用方）。
        // dueDateMap（配置驱动）只用于下方板块「硬筛」+ chip 计数；展示用 displayDueMap（固定 114/25，Kevin 2026-07-05）。
        Map<Long, LocalDate> dueDateMap = computeDueDateMap(pigs, dueType);
        Map<Long, LocalDate> displayDueMap = computeDisplayDueMap(pigs, dueType);

        // r52/r53（邓博 2026-06-30）：分娩/断奶板块按「后台生产配置天数」**硬筛**——只显示已满足对应天数的母猪，
        // 反转 D12X-MP-FARROW-WEANING-001 的「软提示列全部」做法（Kevin 2026-07-01：严格按邓博描述执行）。
        //   FARROW（分娩板块）：配种日 + sow_breed_to_farrow_days ≤ 今天 才显示（满足配怀天数）。
        //   WEANING（断奶板块）：分娩日 + sow_farrow_to_wean_days ≤ 今天 才显示（满足哺乳天数）。
        //   = dueDate ≤ 今天（dueDate 即「基准日 + 对应配置天数」）。无 dueDate（缺配种/分娩基准日）的母猪不进板块。
        //   配置缺失时 computeDueDateMap 返空 map → 不过滤（degrade 不卡空板块，与 filterBreedReady 同款防御）。
        // row257/258：搜耳号时**只跳过这段硬筛**，dueDateMap/displayDueMap 仍照算 —— 搜出来的未到期母猪
        //   照样带预产期/到断奶期与临产角标，工人能一眼看出「这头还差几天」而不是搜不到。
        if (!searchingByEarNo && StringUtils.isNotBlank(dueType) && !dueDateMap.isEmpty()) {
            LocalDate dueToday = LocalDate.now();
            pigs = pigs.stream()
                .filter(p -> {
                    LocalDate due = dueDateMap.get(p.getId());
                    return due != null && !due.isAfter(dueToday);
                })
                .toList();
            if (pigs.isEmpty()) {
                return Collections.emptyList();
            }
        }

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
        // 如 04=国寿黑）；旧静态字典已废，缺则回落原始 code。批量预载一次防 N+1。
        Map<String, String> breedNameMap = loadBreedStrainNameMap(1);
        Map<String, String> strainNameMap = loadBreedStrainNameMap(2);
        // row51「其他猪只」卡：批量取本页各猪上次生长记录日期 MAX(measure_date)（一次 IN 查防 N+1）。
        Set<Long> pageIds = pigs.stream().map(Pig::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, LocalDate> lastMeasureMap = loadLastMeasureDateMap(pageIds);
        List<PigSearchVo> result = new ArrayList<>(pigs.size());
        for (Pig p : pigs) {
            PigSearchVo vo = new PigSearchVo();
            vo.setId(p.getId());
            vo.setEarNo(p.getEarNo());
            vo.setPigSex(p.getPigSex());
            vo.setPigType(p.getPigType());
            // row51「其他猪只」卡：猪只类型中文（与 be-growth PigGrowthServiceImpl.toPigTypeName 同口径）+ 上次测量日期。
            vo.setPigTypeName(toPigTypeName(p.getPigType()));
            vo.setLastMeasureDate(lastMeasureMap.get(p.getId()));
            vo.setCurrentStatus(p.getCurrentStatus());
            // 品种/品系编码 + 中文名（mp 选猪弹层「品种/品系」标签）
            vo.setPigBreedCode(p.getPigBreedCode());
            // #19：品种 breed_info 主表优先 → 字典回落 → code 回落（与品系同源，04=国寿黑而非杜洛克）
            vo.setPigBreedName(resolveBreedStrainName(breedNameMap, p.getPigBreedCode()));
            vo.setPigStrainCode(p.getPigStrainCode());
            // #13：品系 breed_info 主数据优先 → 字典回落 → code 回落
            vo.setPigStrainName(resolveBreedStrainName(strainNameMap, p.getPigStrainCode()));
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
            // 展示值用 displayDueMap（固定 配种日+114 / 分娩日+25），与板块硬筛的 dueDateMap（配置驱动）解耦。
            // 临产/临断奶阈值 = dueDate ≤ today + dueWindowDays（临近窗口内或已过期）即标 due：分娩 5 天 / 断奶 3 天。
            LocalDate dd = displayDueMap.get(p.getId());
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
     * 到期日期计算：算每头母猪的预产期 / 到断奶期。dueDate 同时驱动 r52/r53 板块**硬筛**（dueDate ≤ 今天才进
     * 分娩/断奶板块，邓博 2026-06-30 + Kevin 2026-07-01）与 mp 排序「临产排前」+ badge。
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

    /**
     * 展示用到期日期：预产期 = 配种日 + 114（固定）；断奶期 = 分娩日 + 25（固定）。
     *
     * <p>与 {@link #computeDueDateMap}（读生产周期配置、驱动板块硬筛 + chip 计数）解耦：
     * mp 分娩/断奶选猪卡上的「预产期 / 到断奶期」红字恒为 配种日+114 / 分娩日+25，不随 admin 配置漂移
     * （Kevin 2026-07-05：配置只管筛选，展示固定）。基准日期缺失 → 不入 map（该格不渲染）。</p>
     *
     * @param pigs    候选母猪
     * @param dueType {@code FARROW} / {@code WEANING}；其他值或空 → 空 map
     * @return {@code pigId → 展示到期日}
     */
    private Map<Long, LocalDate> computeDisplayDueMap(List<Pig> pigs, String dueType) {
        if (StringUtils.isBlank(dueType) || pigs.isEmpty()) {
            return Map.of();
        }
        if ("FARROW".equalsIgnoreCase(dueType)) {
            return pigs.stream()
                .filter(p -> p.getId() != null && p.getLastMatingDate() != null)
                .collect(Collectors.toMap(Pig::getId, p -> p.getLastMatingDate().plusDays(GESTATION_DAYS_DISPLAY), (a, b) -> a));
        }
        if ("WEANING".equalsIgnoreCase(dueType)) {
            Set<Long> pigIds = pigs.stream().map(Pig::getId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (pigIds.isEmpty()) {
                return Map.of();
            }
            Map<Long, LocalDate> lastFarrowMap = pigMapper.selectLastFarrowDateByPigIds(pigIds).stream()
                .filter(v -> v.getPigId() != null && v.getLastFarrowDate() != null)
                .collect(Collectors.toMap(PigLastFarrowVo::getPigId, PigLastFarrowVo::getLastFarrowDate, (a, b) -> a));
            Map<Long, LocalDate> dueMap = new HashMap<>(lastFarrowMap.size());
            lastFarrowMap.forEach((pigId, lastFarrow) -> dueMap.put(pigId, lastFarrow.plusDays(LACTATION_DAYS_DISPLAY)));
            return dueMap;
        }
        return Map.of();
    }

    @Override
    public List<PigBarnCountVo> countByBarn(String statusFilter, String sexFilter, String pigTypeFilter, String earNoKeyword, Boolean breedReady, String dueType, Integer minAgeDays) {
        List<String> statuses = parseStatusFilter(statusFilter);
        boolean callerWantsEnd = statuses.contains(PigLifecycle.END.name());
        // row256-258：与 searchByEarKeyword 保持同一条放行规则——传了耳号关键字就跳过
        // 「默认待办窗口」类收窄（minAgeDays 到龄 / breedReady 在场天数 / dueType 到期硬筛）。
        // 本方法的 javadoc 声称「与 search 同口径」，若只有 search 放行、chip 计数不放行，
        // 该声明就是假的：同样参数下 search 返 1 条、barn-count 返空（QA 对抗验收实测复现）。
        // 目前 PigSelectPanel 输入耳号时不重载 chip（Kevin 2026-07-21），故用户暂看不到；
        // 但契约不能留假，一旦哪天恢复重载就会立刻表现为「列表有猪、chip 全 0」。
        boolean searchingByEarNo = StringUtils.isNotBlank(earNoKeyword);
        // row180：出栏 chip 与列表同口径——日龄 >= minAgeDays（到龄肥猪）才计入 chip。
        // 与 searchByEarKeyword 完全一致：COALESCE(birth_date, introduce_date)，无生日 → DATEDIFF 为 NULL 自动剔除。
        // minAgeDays ≤0/null（其余 12 页调用方不传）→ 不过滤（向后兼容，行为不变）。
        boolean applyMinAge = !searchingByEarNo && minAgeDays != null && minAgeDays > 0;

        // pigTypeFilter 支持 CSV（如 'piglet,fattening' 给阉割选猪 = 公的仔猪+育肥猪）；单值退化 .eq，CSV 走 IN。
        // 与 searchByEarKeyword 同口径，否则栋舍 chip 计数与列表条数对不上。
        List<String> pigTypes = StringUtils.isNotBlank(pigTypeFilter)
            ? Arrays.stream(pigTypeFilter.split(",")).map(String::trim).filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList())
            : Collections.emptyList();

        LambdaQueryWrapper<Pig> w = new LambdaQueryWrapper<Pig>()
            .ne(!callerWantsEnd, Pig::getCurrentStatus, PigLifecycle.END.name())
            // row60：栋舍 chip 头数随耳号搜索缩减，与 searchByEarKeyword 同口径（earNoKeyword LIKE）；空 → 不过滤
            .like(StringUtils.isNotBlank(earNoKeyword), Pig::getEarNo, earNoKeyword)
            .eq(StringUtils.isNotBlank(sexFilter), Pig::getPigSex, sexFilter)
            .eq(pigTypes.size() == 1, Pig::getPigType, pigTypes.isEmpty() ? null : pigTypes.get(0))
            .in(pigTypes.size() > 1, Pig::getPigType, pigTypes)
            // row180：出栏选猪日龄 >= minAgeDays，与 searchByEarKeyword 完全一致（{0} 占位 + apply 防注入，
            // COALESCE(birth_date, introduce_date) 同口径），否则 chip 计全部而列表只列到龄 → 对不上。
            .apply(applyMinAge, "DATEDIFF(NOW(), COALESCE(birth_date, introduce_date)) >= {0}", minAgeDays)
            // 无栋舍归属的猪只不计入任何 chip
            .isNotNull(Pig::getBarnId);
        if (!statuses.isEmpty()) {
            w.in(Pig::getCurrentStatus, statuses);
        }

        List<Pig> pigs = pigMapper.selectList(w);
        if (pigs.isEmpty()) {
            return Collections.emptyList();
        }

        // row13：与 searchByEarKeyword 同口径——配种选猪时按最小在场天数过滤，否则栋舍 chip 头数之和对不上列表条数
        if (!searchingByEarNo && Boolean.TRUE.equals(breedReady)) {
            pigs = filterBreedReady(pigs, LocalDateTime.now());
            if (pigs.isEmpty()) {
                return Collections.emptyList();
            }
        }

        // r120：与 searchByEarKeyword 同口径——分娩/断奶板块按生产配置天数硬筛（dueDate ≤ 今天）。
        // 不加此过滤则栋舍 chip 计未到期母猪、列表只列到期母猪 → chip 显 1 而列表空（测试 r120）。
        // 与列表侧 filter（上方 695-706）逻辑一致：配置缺失 → computeDueDateMap 返空 → 不过滤（degrade）。
        if (!searchingByEarNo && StringUtils.isNotBlank(dueType)) {
            Map<Long, LocalDate> dueDateMap = computeDueDateMap(pigs, dueType);
            if (!dueDateMap.isEmpty()) {
                LocalDate dueToday = LocalDate.now();
                pigs = pigs.stream()
                    .filter(p -> {
                        LocalDate due = dueDateMap.get(p.getId());
                        return due != null && !due.isAfter(dueToday);
                    })
                    .toList();
                if (pigs.isEmpty()) {
                    return Collections.emptyList();
                }
            }
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
     * 猪只类型 code → 中文（种母猪 / 种公猪 / 育肥猪 / 仔猪）。row51「其他猪只」卡用，
     * 与 be-growth {@code PigGrowthServiceImpl.toPigTypeName} 显式映射口径一致（带「种」前缀，
     * 不走字典 djs_pig_type 的「母猪 / 公猪」标签）；未知 code → null。
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

    /**
     * 批量取各猪「上次生长记录日期」MAX(measure_date)（row51 search 卡「上次测量」格用）。
     * <p>一次 IN 查全部目标猪的生长记录，内存按 pigId 归约取最大 measure_date，避免逐猪查（N+1）。
     * 无记录的猪不入 map（调用方 get 得 null）。参 be-growth {@code loadLastMeasureDateMap}。</p>
     */
    private Map<Long, LocalDate> loadLastMeasureDateMap(Set<Long> pigIds) {
        if (pigIds == null || pigIds.isEmpty()) {
            return Map.of();
        }
        List<PigGrowth> records = pigGrowthMapper.selectList(new LambdaQueryWrapper<PigGrowth>()
            .select(PigGrowth::getPigId, PigGrowth::getMeasureDate)
            .in(PigGrowth::getPigId, pigIds)
            .isNotNull(PigGrowth::getMeasureDate));
        Map<Long, LocalDate> map = new HashMap<>();
        for (PigGrowth r : records) {
            if (r.getPigId() == null || r.getMeasureDate() == null) {
                continue;
            }
            map.merge(r.getPigId(), r.getMeasureDate(),
                (existing, candidate) -> candidate.isAfter(existing) ? candidate : existing);
        }
        return map;
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

    /** 后备（HB）到配种的最小日龄阈值配置 key（按日龄过滤，区别于其他状态的「在场天数」）。 */
    private static final String RESERVE_BREED_DAYS_KEY = "sow_reserve_to_breed_days";

    /**
     * row13/row35：配种选猪「待配种列表」按后台「母猪生产配置」过滤——两道门，AND 关系：
     * <ol>
     *   <li><b>全局日龄门槛</b>（row35，Kevin 2026-06-22）：列表里所有母猪的<b>日龄</b>
     *       （NOW − birth_date，缺则 introduce_date）须 ≥ {@code sow_reserve_to_breed_days}。
     *       后台「后备-配种天数」既是后备的配种龄阈值，也作整张待配种列表的最小配种日龄——
     *       日龄不到的（如测试态 2/4 日龄异常猪）一律不显。</li>
     *   <li><b>在场天数门槛</b>（row13）：母猪在过日龄门槛后，再按在当前状态的<b>在场天数</b>
     *       ≥ 对应阈值过滤——后备按「后备-配种天数」（刚进后备不足天数不显），断奶/返情/空怀/流产
     *       各自独立天数（再配种前的恢复期）。无对应配置项的状态 → 仅受日龄门槛约束。</li>
     * </ol>
     * 阈值未配置（getValue 返 null）→ 该门槛不过滤（不误删候选）；日龄 / 在场起始日无法判定（null）→ 保留。
     * 日龄口径与列表「N日龄」一致；在场天数口径与卡片「N天」一致 = today − status_started_at。
     *
     * @param pigs 状态白名单过滤后的候选母猪
     * @param now  当前时刻（与卡片 lastEventDays 同口径）
     * @return 满足配置阈值的母猪子集
     */
    private List<Pig> filterBreedReady(List<Pig> pigs, LocalDateTime now) {
        if (pigs.isEmpty()) {
            return pigs;
        }
        // 状态 → 母猪生产配置 key（配种前观察期/恢复期：按「在当前状态的在场天数」过滤）
        // 后备（HB）按在后备的在场天数 ≥「后备-配种天数」过滤：刚进后备不足天数的不进可配种列表。
        Map<String, String> statusKey = Map.of(
            PigLifecycle.HB.name(), RESERVE_BREED_DAYS_KEY,
            PigLifecycle.DN.name(), "sow_wean_to_breed_days",
            PigLifecycle.FQ.name(), "sow_return_to_breed_days",
            PigLifecycle.KH.name(), "sow_empty_to_breed_days",
            PigLifecycle.LC.name(), "sow_abort_to_breed_days");
        List<String> allKeys = new ArrayList<>(statusKey.values());
        allKeys.add(RESERVE_BREED_DAYS_KEY);
        Map<String, Integer> thresholds = productionCycleConfigService.getValuesByKeys(allKeys);
        Integer reserveMinAge = thresholds.get(RESERVE_BREED_DAYS_KEY);
        LocalDate today = now.toLocalDate();
        return pigs.stream().filter(p -> {
            // 门槛 1：全局最小配种日龄。日龄不可判（无生日/引种日）→ 保留（不误删）。
            if (reserveMinAge != null) {
                Integer age = calcAgeDays(p, today);
                if (age != null && age < reserveMinAge) {
                    return false;
                }
            }
            // 门槛 2：再配种恢复期（断奶/返情/空怀/流产）。后备及其他无配置项的 → 仅过门槛 1。
            String key = statusKey.get(p.getCurrentStatus());
            if (key == null) {
                return true;
            }
            Integer minDays = thresholds.get(key);
            if (minDays == null) {
                return true;
            }
            Integer days = calcDaysSince(p.getStatusStartedAt(), now);
            return days == null || days >= minDays;
        }).collect(Collectors.toList());
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
        // TO_FATTEN（admin row162 后备种母猪转育肥）与 TRANSFER 的 payload 同形：
        // newBarnId / newPenId 落位置，newPigType 切类型（转育肥固定 sow → fattening）。
        if (ev == TRANSFER || ev == TO_FATTEN) {
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

        // row251：最大日龄过滤（mp 疫苗药品猪只列表选「育肥猪」时传 fatten_med_max_age_days）。
        // 与 minAgeDays 用同一日龄口径 COALESCE(birth_date, introduce_date)；差别在无生日的猪要放行——
        // DATEDIFF 返 NULL 时比较非真会被剔除，故写成「日龄为空 OR 日龄 ≤ 上限」。null/≤0 → 不拼条件。
        Integer maxAge = query.getMaxAgeDays();
        if (maxAge != null && maxAge > 0) {
            w.apply("(COALESCE(birth_date, introduce_date) IS NULL"
                + " OR DATEDIFF(NOW(), COALESCE(birth_date, introduce_date)) <= {0})", maxAge);
        }

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

    /**
     * 状态流水「变更时间」补时分秒。
     *
     * <p>admin 端各事件表单（引种 / 转移 / 转育肥 …）只让用户选日期，落到
     * {@code PigEventBo.eventAt} 就是 {@code yyyy-MM-dd 00:00:00}，事件台账整行显示成
     * 「2026-07-31 00:00:00」，时分秒恒为 0。规则：</p>
     * <ul>
     *   <li>调用方已给出真实时分秒（mp 各事件页传 {@code new Date()}）→ 原样保留；</li>
     *   <li>只给了日期且这个业务日期就是今天 → 用当前时刻替换（日期不变，只把时分秒补成真实操作时刻）；</li>
     *   <li>只给了日期且是补录历史日期 → 保留 00:00:00（历史那天几点录的不可知，不编造）。</li>
     * </ul>
     *
     * <p>只作用于流水表 {@code t_farm_status_record.change_time}（列表展示 / 排序口径）。
     * 不能顺带改 {@code t_farm_pig_info.status_started_at} 与 {@code duration_days}——
     * 这两个按「日」粒度参与在场天数 / 日龄阈值计算（{@code DAYS.between} 满 24h 才进 1），
     * 加上时分秒会把天数少算一天。</p>
     *
     * @param businessAt 业务时点，**不可为 null**：三个调用点都已各自兜底
     *                   （fireEvent 走 {@code Optional...orElseGet(now)}，两处 INTRO 走
     *                   {@code introduceDate.atStartOfDay()} 且引种日期为空时回落当天）。
     *                   这里显式 requireNonNull 而不是静默回落 now，是因为一旦回落，
     *                   写进流水的就不再是业务日期而是「今天」，属于静默数据错误。
     */
    private static LocalDateTime withOperateClock(LocalDateTime businessAt) {
        Objects.requireNonNull(businessAt, "businessAt must not be null: 调用方需先兜底业务时点");
        LocalDateTime now = LocalDateTime.now();
        boolean dateOnly = LocalTime.MIDNIGHT.equals(businessAt.toLocalTime());
        return dateOnly && businessAt.toLocalDate().equals(now.toLocalDate()) ? now : businessAt;
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
