package org.dromara.djs.breed.event.farrow.service.impl;

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
import org.dromara.djs.breed.event.eartag.domain.PigPigletno;
import org.dromara.djs.breed.event.eartag.mapper.PigPigletnoMapper;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;
import org.dromara.djs.breed.event.farrow.domain.bo.FarrowBo;
import org.dromara.djs.breed.event.farrow.domain.query.FarrowQuery;
import org.dromara.djs.breed.event.farrow.domain.vo.PigFarrowVo;
import org.dromara.djs.breed.event.farrow.event.PigFarrowEvent;
import org.dromara.djs.breed.event.farrow.mapper.PigFarrowMapper;
import org.dromara.djs.breed.event.farrow.service.IFarrowService;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 分娩事件 Service 实现（BRD-EVENT-002 FARROW）。
 *
 * <h3>事务边界</h3>
 * <p>{@link #recordFarrow} 标 {@code @Transactional}：INSERT farrow + fireEvent(FARROW)
 * + publishEvent 同生共死。Spring listener 应通过 {@code @TransactionalEventListener(AFTER_COMMIT)}
 * 异步消费，避免分娩 commit 失败时耳标 listener 已执行。</p>
 *
 * <h3>状态推进</h3>
 * <p>{@code fireEvent(PigStatusEvent.FARROW)} 推动状态机 PH → FM；非 PH 母猪 / 公猪 / 终态由
 * {@code PigStateMachine} 直接抛 ServiceException，无需本 service 重复校验。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FarrowServiceImpl implements IFarrowService {

    /** mp 端 picker 默认上限：单页 50 条防爆。 */
    private static final int RECENT_LIMIT_MAX = 50;
    /** mp 端 picker 默认窗口：最近 7 天。 */
    private static final int RECENT_DAYS = 7;

    private final PigFarrowMapper farrowMapper;
    private final PigMapper pigMapper;
    private final BarnMapper barnMapper;
    private final PenMapper penMapper;
    private final PigPigletnoMapper pigletnoMapper;
    private final IPigCoreService pigCoreService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigFarrowVo recordFarrow(FarrowBo bo) {
        Objects.requireNonNull(bo, "FarrowBo must not be null");

        Pig pig = pigMapper.selectById(bo.getPigId());
        if (pig == null) {
            throw new ServiceException(I18nMessages.t("pig.not_found", bo.getPigId()));
        }

        validate(bo);

        // 1. 写 t_farm_pig_farrow（breedingId 缺时默认取 pig.matingId 最近配种）
        PigFarrow farrow = new PigFarrow();
        farrow.setPigId(pig.getId());
        farrow.setEarNo(pig.getEarNo());
        farrow.setBreedingId(Optional.ofNullable(bo.getBreedingId()).orElse(pig.getMatingId()));
        farrow.setFarrowDate(bo.getFarrowDate());
        farrow.setTotalBorn(bo.getTotalBorn());
        farrow.setLiveBorn(bo.getLiveBorn());
        farrow.setDeadBorn(Optional.ofNullable(bo.getDeadBorn()).orElse(0));
        farrow.setMummyBorn(Optional.ofNullable(bo.getMummyBorn()).orElse(0));
        farrow.setWeakBorn(Optional.ofNullable(bo.getWeakBorn()).orElse(0));
        farrow.setMaleCount(bo.getMaleCount());
        farrow.setFemaleCount(bo.getFemaleCount());
        farrow.setTotalWeight(bo.getTotalWeight());
        farrow.setAvgWeight(bo.getAvgWeight());
        // 胎次：用母猪当前 parity + 1（状态机 applyEventSideEffects 也会同步 +1，两端一致）
        farrow.setParity(Optional.ofNullable(pig.getParity()).orElse(0) + 1);
        farrow.setBarnName(resolveBarnName(pig.getBarnId()));
        farrow.setPenName(resolvePenName(pig.getPenId()));
        farrow.setRemark(bo.getRemark());
        farrow.setDelFlag("0");
        farrowMapper.insert(farrow);

        // 2. 触发状态机（PH → FM），同事务；非法 transition 由状态机抛
        PigEventBo eventBo = new PigEventBo();
        eventBo.setPigId(pig.getId());
        eventBo.setEventType(PigStatusEvent.FARROW);
        eventBo.setRelatedEventId(farrow.getId());
        eventBo.setEventAt(bo.getFarrowDate());
        pigCoreService.fireEvent(eventBo);

        // 3. 发布 Spring event（耳标域 listener 异步消费，AFTER_COMMIT）
        eventPublisher.publishEvent(new PigFarrowEvent(this, farrow));

        log.info("[BRD-EVENT-002] recordFarrow pigId={} earNo={} farrowId={} liveBorn={} parity={}",
            pig.getId(), pig.getEarNo(), farrow.getId(), farrow.getLiveBorn(), farrow.getParity());

        return toVo(farrow, 0, farrow.getLiveBorn());
    }

    @Override
    public TableDataInfo<PigFarrowVo> queryPage(FarrowQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PigFarrow> w = Wrappers.<PigFarrow>lambdaQuery()
            .eq(query.getPigId() != null, PigFarrow::getPigId, query.getPigId())
            .eq(StringUtils.isNotBlank(query.getEarNo()), PigFarrow::getEarNo, query.getEarNo())
            .ge(query.getBeginDate() != null, PigFarrow::getFarrowDate, query.getBeginDate())
            .le(query.getEndDate() != null, PigFarrow::getFarrowDate, query.getEndDate())
            .orderByDesc(PigFarrow::getFarrowDate, PigFarrow::getId);
        Page<PigFarrowVo> page = farrowMapper.selectVoPage(pageQuery.build(), w);
        enrichTaggedCounts(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<PigFarrowVo> queryRecent(Long operatorId, int limit) {
        int effective = Math.min(limit <= 0 ? RECENT_LIMIT_MAX : limit, RECENT_LIMIT_MAX);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RECENT_DAYS);
        LambdaQueryWrapper<PigFarrow> w = Wrappers.<PigFarrow>lambdaQuery()
            .ge(PigFarrow::getFarrowDate, cutoff)
            .orderByDesc(PigFarrow::getFarrowDate, PigFarrow::getId)
            .last("LIMIT " + effective);
        List<PigFarrowVo> rows = farrowMapper.selectVoList(w);
        enrichTaggedCounts(rows);
        return rows;
    }

    /**
     * 批量回填 tagged / remaining（避免 N+1，单次 selectList 全 farrow 的耳标后内存分组）。
     */
    private void enrichTaggedCounts(List<PigFarrowVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> farrowIds = rows.stream().map(PigFarrowVo::getId).filter(Objects::nonNull).toList();
        if (farrowIds.isEmpty()) {
            return;
        }
        List<PigPigletno> logs = pigletnoMapper.selectList(
            Wrappers.<PigPigletno>lambdaQuery().in(PigPigletno::getFarrowId, farrowIds));
        Map<Long, Integer> counts = new HashMap<>();
        for (PigPigletno log : logs) {
            counts.merge(log.getFarrowId(), 1, Integer::sum);
        }
        for (PigFarrowVo vo : rows) {
            int tagged = counts.getOrDefault(vo.getId(), 0);
            vo.setTagged(tagged);
            int live = Optional.ofNullable(vo.getLiveBorn()).orElse(0);
            vo.setRemaining(Math.max(0, live - tagged));
        }
    }

    /** 业务一致性校验（结构校验已走 JSR-303）。 */
    private void validate(FarrowBo bo) {
        int total = Optional.ofNullable(bo.getTotalBorn()).orElse(0);
        int live = Optional.ofNullable(bo.getLiveBorn()).orElse(0);
        if (live > total) {
            throw new ServiceException(I18nMessages.t("farrow.live_exceeds_total", live, total));
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

    private PigFarrowVo toVo(PigFarrow e, int tagged, int liveBorn) {
        PigFarrowVo v = new PigFarrowVo();
        v.setId(e.getId());
        v.setPigId(e.getPigId());
        v.setEarNo(e.getEarNo());
        v.setBreedingId(e.getBreedingId());
        v.setFarrowDate(e.getFarrowDate());
        v.setTotalBorn(e.getTotalBorn());
        v.setLiveBorn(e.getLiveBorn());
        v.setDeadBorn(e.getDeadBorn());
        v.setMummyBorn(e.getMummyBorn());
        v.setWeakBorn(e.getWeakBorn());
        v.setMaleCount(e.getMaleCount());
        v.setFemaleCount(e.getFemaleCount());
        v.setTotalWeight(e.getTotalWeight());
        v.setAvgWeight(e.getAvgWeight());
        v.setParity(e.getParity());
        v.setOperatorId(e.getOperatorId());
        v.setBarnName(e.getBarnName());
        v.setPenName(e.getPenName());
        v.setRemark(e.getRemark());
        v.setTagged(tagged);
        v.setRemaining(Math.max(0, liveBorn - tagged));
        return v;
    }
}
