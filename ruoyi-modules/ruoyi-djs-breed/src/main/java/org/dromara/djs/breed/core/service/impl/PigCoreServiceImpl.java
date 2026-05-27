package org.dromara.djs.breed.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.PigStatusRecord;
import org.dromara.djs.breed.core.domain.bo.PigCreateBo;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.domain.query.PigQuery;
import org.dromara.djs.breed.core.domain.query.PigStatusRecordQuery;
import org.dromara.djs.breed.core.domain.vo.PigDetailVo;
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
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    public PigCoreServiceImpl(PigMapper pigMapper,
                              PigStatusRecordMapper statusRecordMapper,
                              PigStateMachine stateMachine,
                              ApplicationEventPublisher eventPublisher,
                              BarnMapper barnMapper,
                              PenMapper penMapper) {
        this.pigMapper = pigMapper;
        this.statusRecordMapper = statusRecordMapper;
        this.stateMachine = stateMachine;
        this.eventPublisher = eventPublisher;
        this.barnMapper = barnMapper;
        this.penMapper = penMapper;
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

        PigLifecycle from = parseLifecycle(pig.getCurrentStatus(), pig.getId());
        PigLifecycle to = stateMachine.nextStatus(from, bo.getEventType(), pig.getPigSex(), bo.getPayload());

        LocalDateTime eventAt = Optional.ofNullable(bo.getEventAt()).orElseGet(LocalDateTime::now);

        // 1. 写 status_record
        PigStatusRecord record = new PigStatusRecord();
        record.setPigId(pig.getId());
        record.setEarNo(pig.getEarNo());
        record.setOldStatus(from.name());
        record.setNewStatus(to.name());
        record.setEventType(bo.getEventType().name());
        record.setRelatedEventId(bo.getRelatedEventId());
        record.setChangeTime(eventAt);
        record.setDurationDays(calcDurationDays(pig.getStatusStartedAt(), eventAt));
        statusRecordMapper.insert(record);

        // 2. 更新 pig（同事务，乐观锁强制）
        if (to != from) {
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

        // 初始 lifecycle：公猪 → BOAR_ACTIVE；母猪 / piglet / fattening → HB
        PigLifecycle initial = "M".equals(bo.getPigSex()) && "boar".equals(bo.getPigType())
            ? PigLifecycle.BOAR_ACTIVE : PigLifecycle.HB;

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
        pig.setCurrentStatus(initial.name());
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
        record.setNewStatus(initial.name());
        record.setEventType(PigStatusEvent.INTRO.name());
        record.setChangeTime(now);
        statusRecordMapper.insert(record);

        eventPublisher.publishEvent(new PigStateChangedEvent(this, record, pig, null, initial));

        log.info("[BRD-CORE-001] createPig pigId={} earNo={} initial={} pigType={}",
            pig.getId(), pig.getEarNo(), initial, pig.getPigType());

        return pig;
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
        PigDetailVo detail = toDetailVo(vo);
        LambdaQueryWrapper<PigStatusRecord> w = new LambdaQueryWrapper<PigStatusRecord>()
            .eq(PigStatusRecord::getPigId, pigId)
            .orderByDesc(PigStatusRecord::getChangeTime, PigStatusRecord::getId)
            .last("LIMIT " + RECENT_HISTORY_LIMIT);
        detail.setRecentHistory(statusRecordMapper.selectVoList(w));
        return detail;
    }

    @Override
    public TableDataInfo<PigVo> queryPage(PigQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<Pig> wrapper = buildWrapper(query);
        Page<PigVo> page = pigMapper.selectVoPage(pageQuery.build(), wrapper);
        enrichBarnPenCodes(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 批量回填 barnCode / penCode（Pig 与 Barn/Pen 不同聚合，VO 出参用 code 而非
     * snowflake id 给用户看；列表场景用 selectBatchIds 一次性查，避免 N+1）。
     */
    private void enrichBarnPenCodes(List<PigVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> barnIds = rows.stream().map(PigVo::getBarnId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> penIds = rows.stream().map(PigVo::getPenId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> barnCodeMap = barnIds.isEmpty() ? Map.of()
            : barnMapper.selectBatchIds(barnIds).stream()
                .collect(Collectors.toMap(Barn::getId, Barn::getBarnCode, (a, b) -> a));
        Map<Long, String> penCodeMap = penIds.isEmpty() ? Map.of()
            : penMapper.selectBatchIds(penIds).stream()
                .collect(Collectors.toMap(Pen::getId, Pen::getPenCode, (a, b) -> a));
        for (PigVo vo : rows) {
            if (vo.getBarnId() != null) {
                vo.setBarnCode(barnCodeMap.get(vo.getBarnId()));
            }
            if (vo.getPenId() != null) {
                vo.setPenCode(penCodeMap.get(vo.getPenId()));
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
                                                Integer limit) {
        int effectiveLimit = clampLimit(limit);
        // statusFilter CSV："HB,PZ" → IN ('HB','PZ')；如果显式含 END（如燎毛工序选已出栏猪），跳过下方 .ne(END) 默认排除
        List<String> statuses = parseStatusFilter(statusFilter);
        boolean callerWantsEnd = statuses.contains(PigLifecycle.END.name());

        LambdaQueryWrapper<Pig> w = new LambdaQueryWrapper<Pig>()
            // 默认排除 END 猪只——给事件录入 picker 用（配种 / 转栏等 END 不能再触发的事件）；
            // 但 statusFilter 显式声明要 END（如 WMS-PIG-001 燎毛工序）时放行
            .ne(!callerWantsEnd, Pig::getCurrentStatus, PigLifecycle.END.name())
            .like(StringUtils.isNotBlank(earNoKeyword), Pig::getEarNo, earNoKeyword)
            .eq(StringUtils.isNotBlank(sexFilter), Pig::getPigSex, sexFilter)
            .eq(StringUtils.isNotBlank(pigTypeFilter), Pig::getPigType, pigTypeFilter)
            .orderByDesc(Pig::getId)
            .last("LIMIT " + effectiveLimit);

        if (!statuses.isEmpty()) {
            w.in(Pig::getCurrentStatus, statuses);
        }

        List<Pig> pigs = pigMapper.selectList(w);
        if (pigs.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量 enrich barnCode/penCode（与 queryPage 一致，避免 N+1）
        Set<Long> barnIds = pigs.stream().map(Pig::getBarnId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> penIds = pigs.stream().map(Pig::getPenId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> barnCodeMap = barnIds.isEmpty() ? Map.of()
            : barnMapper.selectBatchIds(barnIds).stream()
                .collect(Collectors.toMap(Barn::getId, Barn::getBarnCode, (a, b) -> a));
        Map<Long, String> penCodeMap = penIds.isEmpty() ? Map.of()
            : penMapper.selectBatchIds(penIds).stream()
                .collect(Collectors.toMap(Pen::getId, Pen::getPenCode, (a, b) -> a));

        List<PigSearchVo> result = new ArrayList<>(pigs.size());
        for (Pig p : pigs) {
            PigSearchVo vo = new PigSearchVo();
            vo.setId(p.getId());
            vo.setEarNo(p.getEarNo());
            vo.setPigSex(p.getPigSex());
            vo.setPigType(p.getPigType());
            vo.setCurrentStatus(p.getCurrentStatus());
            if (p.getBarnId() != null) {
                vo.setBarnCode(barnCodeMap.get(p.getBarnId()));
            }
            if (p.getPenId() != null) {
                vo.setPenCode(penCodeMap.get(p.getPenId()));
            }
            result.add(vo);
        }
        return result;
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
            .eq(StringUtils.isNotBlank(query.getEarNo()), PigStatusRecord::getEarNo, query.getEarNo())
            .eq(StringUtils.isNotBlank(query.getEventType()), PigStatusRecord::getEventType, query.getEventType())
            .eq(StringUtils.isNotBlank(query.getNewStatus()), PigStatusRecord::getNewStatus, query.getNewStatus())
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
        w.eq(StringUtils.isNotBlank(query.getEarNo()), Pig::getEarNo, query.getEarNo())
            .eq(StringUtils.isNotBlank(query.getPigSex()), Pig::getPigSex, query.getPigSex())
            .eq(StringUtils.isNotBlank(query.getPigType()), Pig::getPigType, query.getPigType())
            .eq(StringUtils.isNotBlank(query.getCurrentStatus()), Pig::getCurrentStatus, query.getCurrentStatus())
            .eq(query.getBarnId() != null, Pig::getBarnId, query.getBarnId())
            .eq(query.getPenId() != null, Pig::getPenId, query.getPenId())
            .ne(Boolean.TRUE.equals(query.getExcludeEnd()), Pig::getCurrentStatus, PigLifecycle.END.name())
            .orderByDesc(Pig::getId);
        return w;
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
        d.setPigStrainCode(src.getPigStrainCode());
        d.setCurrentStatus(src.getCurrentStatus());
        d.setStatusStartedAt(src.getStatusStartedAt());
        d.setEndReason(src.getEndReason());
        d.setBirthDate(src.getBirthDate());
        d.setIntroduceDate(src.getIntroduceDate());
        d.setParity(src.getParity());
        d.setBarnId(src.getBarnId());
        d.setBarnCode(src.getBarnCode());
        d.setPenId(src.getPenId());
        d.setPenCode(src.getPenCode());
        d.setMatingId(src.getMatingId());
        d.setMotherEar(src.getMotherEar());
        d.setFatherEar(src.getFatherEar());
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
