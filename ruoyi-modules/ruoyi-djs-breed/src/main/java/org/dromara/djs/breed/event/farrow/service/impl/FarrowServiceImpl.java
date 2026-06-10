package org.dromara.djs.breed.event.farrow.service.impl;

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
import org.dromara.djs.breed.event.eartag.domain.PigPigletno;
import org.dromara.djs.breed.event.eartag.mapper.PigPigletnoMapper;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;
import org.dromara.djs.breed.event.farrow.domain.bo.FarrowBo;
import org.dromara.djs.breed.event.farrow.domain.query.FarrowQuery;
import org.dromara.djs.breed.event.farrow.domain.vo.FarrowBarnCountVo;
import org.dromara.djs.breed.event.farrow.domain.vo.FarrowLitterVo;
import org.dromara.djs.breed.event.farrow.domain.vo.FarrowPickerVo;
import org.dromara.djs.breed.event.farrow.domain.vo.PigFarrowVo;
import org.dromara.djs.breed.event.farrow.mapper.PigFarrowMapper;
import org.dromara.djs.breed.event.farrow.service.IFarrowService;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 分娩事件 Service 实现（BRD-EVENT-002 FARROW）。
 *
 * <h3>事务边界</h3>
 * <p>{@link #recordFarrow} 标 {@code @Transactional}：INSERT farrow + fireEvent(FARROW)
 * + publishEvent 同生共死。Spring listener 应通过 {@code @TransactionalEventListener(AFTER_COMMIT)}
 * 异步消费，避免分娩 commit 失败时耳标 listener 已执行。</p>
 *
 * <h3>状态推进</h3>
 * <p>{@code fireEvent(PigStatusEvent.FARROW)} 推动状态机 PZ → FM；非 PZ 母猪 / 公猪 / 终态由
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigFarrowVo recordFarrow(FarrowBo bo) {
        Objects.requireNonNull(bo, "FarrowBo must not be null");

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
        // 原型 93 多分类仔猪字段（BRD-FIX-MP-EVENT-BREED-IA-001）：透传入库，缺省 0
        farrow.setHealthyMale(Optional.ofNullable(bo.getHealthyMale()).orElse(0));
        farrow.setHealthyFemale(Optional.ofNullable(bo.getHealthyFemale()).orElse(0));
        farrow.setWeakRaisedMale(Optional.ofNullable(bo.getWeakRaisedMale()).orElse(0));
        farrow.setWeakRaisedFemale(Optional.ofNullable(bo.getWeakRaisedFemale()).orElse(0));
        farrow.setWeakCulled(Optional.ofNullable(bo.getWeakCulled()).orElse(0));
        farrow.setDeformedBorn(Optional.ofNullable(bo.getDeformedBorn()).orElse(0));
        farrow.setTotalWeight(bo.getTotalWeight());
        farrow.setAvgWeight(bo.getAvgWeight());
        // 胎次：用母猪当前 parity + 1（状态机 applyEventSideEffects 也会同步 +1，两端一致）
        farrow.setParity(Optional.ofNullable(pig.getParity()).orElse(0) + 1);
        farrow.setBarnName(resolveBarnName(pig.getBarnId()));
        farrow.setPenName(resolvePenName(pig.getPenId()));
        farrow.setProofOssIds(bo.getProofOssIds());
        farrow.setRemark(bo.getRemark());
        farrow.setOperatorId(bo.getOperatorId() != null ? bo.getOperatorId() : LoginHelper.getUserId());
        farrow.setDelFlag("0");
        farrowMapper.insert(farrow);

        // 2. 触发状态机（PZ → FM），同事务；非法 transition 由状态机抛
        PigEventBo eventBo = new PigEventBo();
        eventBo.setPigId(pig.getId());
        eventBo.setEventType(PigStatusEvent.FARROW);
        eventBo.setRelatedEventId(farrow.getId());
        eventBo.setEventAt(bo.getFarrowDate());
        pigCoreService.fireEvent(eventBo);

        // 仔猪个体由 BRD-EVENT-003 耳标流程创建（用户 mp 端录数量+性别后 batchTag），
        // 分娩只记窝产仔数；by-design 无 farrow→eartag 事件联动，故不发布 Spring event。
        log.info("[BRD-EVENT-002] recordFarrow pigId={} earNo={} farrowId={} liveBorn={} parity={}",
            pig.getId(), pig.getEarNo(), farrow.getId(), farrow.getLiveBorn(), farrow.getParity());

        return toVo(farrow, 0, farrow.getLiveBorn());
    }

    @Override
    public TableDataInfo<PigFarrowVo> queryPage(FarrowQuery query, PageQuery pageQuery) {
        LocalDateTime beginAt = query.getBeginDate() != null ? query.getBeginDate().atStartOfDay() : null;
        LocalDateTime endBefore = query.getEndDate() != null ? query.getEndDate().plusDays(1).atStartOfDay() : null;
        LambdaQueryWrapper<PigFarrow> w = Wrappers.<PigFarrow>lambdaQuery()
            .eq(query.getPigId() != null, PigFarrow::getPigId, query.getPigId())
            .eq(StringUtils.isNotBlank(query.getEarNo()), PigFarrow::getEarNo, query.getEarNo())
            .ge(beginAt != null, PigFarrow::getFarrowDate, beginAt)
            .lt(endBefore != null, PigFarrow::getFarrowDate, endBefore)
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
     * D9 FarrowPicker 反查：按母猪 earNo 查最近 N 次仍有 remain 的分娩。
     *
     * <p>实现：</p>
     * <ul>
     *   <li>1. earNo 直接 ear_no eq（PigFarrow 实体冗余了 earNo，无需 join pig_info）</li>
     *   <li>2. LIMIT (limit * 2) 防止"刚好 N 条全贴满"时返空——多拉一些后内存过滤 remain</li>
     *   <li>3. enrich tagged → 过滤 remainEartag > 0 → 截取 limit 条</li>
     * </ul>
     */
    @Override
    public List<FarrowPickerVo> queryRecentByMotherEarNo(String motherEarNo, int limit) {
        if (StringUtils.isBlank(motherEarNo)) {
            return List.of();
        }
        int effective = Math.min(limit <= 0 ? 5 : limit, 20);
        int rawFetch = effective * 2;
        LambdaQueryWrapper<PigFarrow> w = Wrappers.<PigFarrow>lambdaQuery()
            .eq(PigFarrow::getEarNo, motherEarNo)
            .orderByDesc(PigFarrow::getFarrowDate, PigFarrow::getId)
            .last("LIMIT " + rawFetch);
        List<PigFarrowVo> rows = farrowMapper.selectVoList(w);
        if (rows.isEmpty()) {
            return List.of();
        }
        enrichTaggedCounts(rows);
        List<FarrowPickerVo> picker = new ArrayList<>();
        for (PigFarrowVo r : rows) {
            int tagged = Optional.ofNullable(r.getTagged()).orElse(0);
            int remain = Optional.ofNullable(r.getRemaining()).orElse(0);
            // 已贴满的分娩 picker 不显示（工人不会再选）
            if (remain <= 0) {
                continue;
            }
            FarrowPickerVo vo = new FarrowPickerVo();
            vo.setId(r.getId());
            vo.setMotherEarNo(r.getEarNo());
            vo.setFarrowDate(r.getFarrowDate());
            vo.setPigletNum(r.getLiveBorn());
            vo.setTaggedEartag(tagged);
            vo.setRemainEartag(remain);
            vo.setParity(r.getParity());
            picker.add(vo);
            if (picker.size() >= effective) {
                break;
            }
        }
        return picker;
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

    @Override
    public List<FarrowLitterVo> queryPendingLitters(String motherEarNo, String barnName) {
        // 拉候选窝（可选母猪 / 栋舍过滤）；多拉一些后内存按 remain > 0 过滤
        LambdaQueryWrapper<PigFarrow> w = Wrappers.<PigFarrow>lambdaQuery()
            .eq(StringUtils.isNotBlank(motherEarNo), PigFarrow::getEarNo, motherEarNo)
            .eq(StringUtils.isNotBlank(barnName), PigFarrow::getBarnName, barnName)
            .orderByDesc(PigFarrow::getFarrowDate, PigFarrow::getId)
            .last("LIMIT 200");
        List<PigFarrowVo> rows = farrowMapper.selectVoList(w);
        if (rows.isEmpty()) {
            return List.of();
        }
        enrichTaggedCounts(rows);
        LocalDate today = LocalDate.now();
        List<FarrowLitterVo> result = new ArrayList<>();
        for (PigFarrowVo r : rows) {
            int remain = Optional.ofNullable(r.getRemaining()).orElse(0);
            if (remain <= 0) {
                continue;
            }
            FarrowLitterVo vo = new FarrowLitterVo();
            vo.setId(r.getId());
            vo.setMotherPigId(r.getPigId());
            vo.setMotherEarNo(r.getEarNo());
            vo.setFarrowDate(r.getFarrowDate());
            vo.setParity(r.getParity());
            vo.setLiveBorn(r.getLiveBorn());
            vo.setTaggedEartag(Optional.ofNullable(r.getTagged()).orElse(0));
            vo.setRemainEartag(remain);
            vo.setMaleCount(r.getMaleCount());
            vo.setFemaleCount(r.getFemaleCount());
            vo.setBarnName(r.getBarnName());
            vo.setPenName(r.getPenName());
            // 日龄 = NOW - farrowDate（仔猪日龄）；farrowDate 缺时 null（mp 端该格不渲染）
            if (r.getFarrowDate() != null) {
                vo.setAgeDays((int) ChronoUnit.DAYS.between(r.getFarrowDate().toLocalDate(), today));
            }
            result.add(vo);
            if (result.size() >= 60) {
                break;
            }
        }
        return result;
    }

    @Override
    public List<FarrowBarnCountVo> countPendingLittersByBarn() {
        // 全量待打标窝（不带栋舍过滤），按 barn_name 内存聚合
        List<FarrowLitterVo> litters = queryPendingLitters(null, null);
        Map<String, Integer> byBarn = new TreeMap<>();
        for (FarrowLitterVo l : litters) {
            String barn = l.getBarnName();
            if (StringUtils.isBlank(barn)) {
                // barn_name 为空的窝归"未分配"，不进 chip
                continue;
            }
            byBarn.merge(barn, 1, Integer::sum);
        }
        List<FarrowBarnCountVo> chips = new ArrayList<>(byBarn.size());
        for (Map.Entry<String, Integer> e : byBarn.entrySet()) {
            FarrowBarnCountVo c = new FarrowBarnCountVo();
            c.setBarnName(e.getKey());
            c.setCount(e.getValue());
            chips.add(c);
        }
        return chips;
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
        v.setHealthyMale(e.getHealthyMale());
        v.setHealthyFemale(e.getHealthyFemale());
        v.setWeakRaisedMale(e.getWeakRaisedMale());
        v.setWeakRaisedFemale(e.getWeakRaisedFemale());
        v.setWeakCulled(e.getWeakCulled());
        v.setDeformedBorn(e.getDeformedBorn());
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
