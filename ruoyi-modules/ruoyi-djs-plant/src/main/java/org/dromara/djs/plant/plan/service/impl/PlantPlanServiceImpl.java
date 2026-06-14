package org.dromara.djs.plant.plan.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.domain.PlantPlan;
import org.dromara.djs.plant.plan.domain.bo.PlantDetailInputBo;
import org.dromara.djs.plant.plan.domain.bo.PlantFinishBo;
import org.dromara.djs.plant.plan.domain.bo.PlantPlanCreateBo;
import org.dromara.djs.plant.plan.domain.bo.PlantPlanUpdateBo;
import org.dromara.djs.plant.plan.domain.bo.PlantStartBo;
import org.dromara.djs.plant.plan.domain.query.PlantPlanQuery;
import org.dromara.djs.plant.plan.domain.vo.PlantDetailsVo;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanDetailVo;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanGanttVo;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanStatsVo;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanSummaryVo;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanVo;
import org.dromara.djs.plant.plan.domain.vo.PlotByZoneVo;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plan.mapper.PlantPlanMapper;
import org.dromara.djs.plant.plan.service.IPlantPlanService;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.dromara.djs.plant.zone.domain.PlotZone;
import org.dromara.djs.plant.zone.mapper.PlotZoneMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 种植计划 Service 实现（PLT-PLAN-001）。
 *
 * <h3>业务核心：派生字段算法</h3>
 * <ul>
 *   <li>{@code plant_start_day} = {@code LocalDate.of(planYear, plantMonth, periodToDay(plantPeriod))}
 *       其中 periodToDay：05→5 / 15→15 / 25→25</li>
 *   <li>{@code earliest_harvestdate} = plant_start_day + crop.min_cycle（天）</li>
 *   <li>{@code last_harvestdate} = plant_start_day + crop.max_cycle（天）</li>
 *   <li>{@code expected_yield} = plot.plot_area × crop.predicted_per</li>
 *   <li>主表聚合 4 字段通过 {@link PlantPlanMapper#recalcAggregates} 单 SQL 重算</li>
 * </ul>
 *
 * <h3>编码生成（plan_no）</h3>
 * <p>inline 实现 {@code PLAN-yyyy-NNN}：查同年最大 plan_no → +1 → UNIQUE(plan_no) 兜底。
 * 走 {@link IBizCodeGenerator}（BizCodeType.PLAN_NO，{@code daily_reset=2} 按年重置）。
 * D9 closing Group B 已在 BizCodeGeneratorImpl 加入 {@code {yyyy}} / {@code {seq3}} 占位符及"按年重置"分支。</p>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Slf4j
@Service
public class PlantPlanServiceImpl extends DjsBaseServiceImpl<PlantPlanMapper, PlantPlan>
    implements IPlantPlanService {

    private final PlantDetailsMapper detailsMapper;
    private final CropInfoMapper cropMapper;
    private final PlotInfoMapper plotMapper;
    private final PlotZoneMapper zoneMapper;
    private final PlantWorkTeamMapper teamMapper;
    private final IBizCodeGenerator bizCodeGenerator;

    public PlantPlanServiceImpl(
        PlantPlanMapper baseMapper,
        PlantDetailsMapper detailsMapper,
        CropInfoMapper cropMapper,
        PlotInfoMapper plotMapper,
        PlotZoneMapper zoneMapper,
        PlantWorkTeamMapper teamMapper,
        IBizCodeGenerator bizCodeGenerator) {
        super(baseMapper);
        this.detailsMapper = detailsMapper;
        this.cropMapper = cropMapper;
        this.plotMapper = plotMapper;
        this.zoneMapper = zoneMapper;
        this.teamMapper = teamMapper;
        this.bizCodeGenerator = bizCodeGenerator;
    }

    // ============================================================
    // 查询
    // ============================================================

    @Override
    public TableDataInfo<PlantPlanVo> queryPageList(PlantPlanQuery query, PageQuery pageQuery) {
        Page<PlantPlanVo> page = baseMapper.selectVoPage(pageQuery.build(), buildWrapper(query));
        enrichCrop(page.getRecords());
        enrichAggregates(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<PlantPlanVo> queryList(PlantPlanQuery query) {
        List<PlantPlanVo> list = baseMapper.selectVoList(buildWrapper(query));
        enrichCrop(list);
        enrichAggregates(list);
        return list;
    }

    @Override
    public PlantPlanDetailVo queryDetailById(Long id) {
        PlantPlanVo planVo = baseMapper.selectVoById(id);
        if (planVo == null) {
            throw new ServiceException("种植计划不存在或已删除：" + id);
        }
        enrichCrop(Collections.singletonList(planVo));
        List<PlantDetailsVo> details = detailsMapper.selectVoList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getPlantId, id)
                .orderByAsc(PlantDetails::getPlantMonth)
                .orderByAsc(PlantDetails::getPlantPeriod));
        enrichDetails(details);
        PlantPlanDetailVo result = new PlantPlanDetailVo();
        result.setPlan(planVo);
        result.setDetails(details);
        return result;
    }

    private LambdaQueryWrapper<PlantPlan> buildWrapper(PlantPlanQuery query) {
        LambdaQueryWrapper<PlantPlan> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(PlantPlan::getId);
        }
        wrapper.like(StringUtils.isNotBlank(query.getPlanNo()), PlantPlan::getPlanNo, query.getPlanNo())
            .eq(query.getPlanYear() != null, PlantPlan::getPlanYear, query.getPlanYear())
            .eq(StringUtils.isNotBlank(query.getPlanSeason()), PlantPlan::getPlanSeason, query.getPlanSeason())
            .eq(query.getCropId() != null, PlantPlan::getCropId, query.getCropId())
            .eq(StringUtils.isNotBlank(query.getPlantStatus()), PlantPlan::getPlantStatus, query.getPlantStatus())
            .eq(query.getQueryCreateBy() != null, PlantPlan::getCreateBy, query.getQueryCreateBy())
            .apply(query.getQueryUpdateTime() != null, "DATE(update_time) = {0}", query.getQueryUpdateTime());
        applyPlanDateRange(wrapper, query.getBeginPlanDate(), query.getEndPlanDate());
        wrapper.orderByDesc(PlantPlan::getId);
        return wrapper;
    }

    /**
     * 计划日期范围过滤：按"该计划的最早开始日期 earliestBegindate 落在 [begin,end] 内"过滤主表
     * （原型「计划日期」改为时间范围搜索，列表展示口径一致）。
     *
     * <p>earliestBegindate 非主表存储列，是明细派生值
     * {@code MIN(DATE(CONCAT(plan_year,'-',LPAD(plant_month,2,'0'),'-',plant_period)))}，
     * 故用相关子查询 + HAVING 在 details 上算出 MIN 后再与范围比较：</p>
     *
     * <pre>
     * EXISTS (SELECT 1 FROM t_plant_plant_details d
     *          WHERE d.plant_id = t_plant_plant_plan.id AND d.del_flag = '0'
     *          HAVING MIN(DATE(CONCAT(t_plant_plant_plan.plan_year,'-',LPAD(d.plant_month,2,'0'),'-',d.plant_period)))
     *                 BETWEEN {0} AND {1})
     * </pre>
     *
     * <p>plan_year 取外层主表列（相关子查询合法引用外层），无明细的计划自然不命中（EXISTS=false）。
     * 仅传 begin 或仅传 end 时退化为单边 {@code >=} / {@code <=}。</p>
     */
    private void applyPlanDateRange(LambdaQueryWrapper<PlantPlan> wrapper,
                                    java.util.Date begin, java.util.Date end) {
        if (begin == null && end == null) {
            return;
        }
        String minExpr = "MIN(DATE(CONCAT(t_plant_plant_plan.plan_year, '-', "
            + "LPAD(d.plant_month, 2, '0'), '-', d.plant_period)))";
        StringBuilder sub = new StringBuilder(
            "EXISTS (SELECT 1 FROM t_plant_plant_details d "
                + "WHERE d.plant_id = t_plant_plant_plan.id AND d.del_flag = '0' HAVING ");
        if (begin != null && end != null) {
            wrapper.apply(sub.append(minExpr).append(" BETWEEN {0} AND {1})").toString(), begin, end);
        } else if (begin != null) {
            wrapper.apply(sub.append(minExpr).append(" >= {0})").toString(), begin);
        } else {
            wrapper.apply(sub.append(minExpr).append(" <= {0})").toString(), end);
        }
    }

    /**
     * 按 cropId 批量 enrich {@code cropName} + {@code cropImage}（crop_image_preview）。
     */
    private void enrichCrop(List<PlantPlanVo> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        Set<Long> cropIds = list.stream()
            .map(PlantPlanVo::getCropId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (cropIds.isEmpty()) {
            return;
        }
        Map<Long, CropInfo> cropMap = cropMapper.selectByIds(cropIds).stream()
            .collect(Collectors.toMap(CropInfo::getId, c -> c, (a, b) -> a));
        for (PlantPlanVo vo : list) {
            CropInfo crop = cropMap.get(vo.getCropId());
            if (crop != null) {
                vo.setCropName(crop.getCropName());
                vo.setCropImage(crop.getCropImagePreview());
            }
        }
    }

    /**
     * 按 planId 批量聚合明细回填列表 enrich 字段（FIX-PLT-AD-PLAN-001）：
     * 预计产量 SUM / 实际产量 SUM / 已完成地块数 COUNT / 计划完成率 / 最早最晚开始日期。
     *
     * <p>完成率 = finishedPlot / totalPlot × 100，向半进位保留 1 位，0~100；
     * totalPlot 为 0 或 null 时返 0。产量口径 kg（决策 #7）。</p>
     */
    private void enrichAggregates(List<PlantPlanVo> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        List<Long> planIds = list.stream()
            .map(PlantPlanVo::getId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (planIds.isEmpty()) {
            return;
        }
        Map<Long, Map<String, Object>> aggMap = baseMapper.selectListAggregates(planIds).stream()
            .filter(row -> row.get("planId") != null)
            .collect(Collectors.toMap(
                row -> ((Number) row.get("planId")).longValue(), row -> row, (a, b) -> a));

        for (PlantPlanVo vo : list) {
            Map<String, Object> row = aggMap.get(vo.getId());
            BigDecimal expected = row == null ? BigDecimal.ZERO : toBigDecimal(row.get("expectedYield"));
            BigDecimal actual = row == null ? BigDecimal.ZERO : toBigDecimal(row.get("actualYield"));
            int finished = row == null ? 0 : toInt(row.get("finishedPlot"));
            vo.setExpectedYield(expected);
            vo.setActualYield(actual);
            vo.setFinishedPlot(finished);
            vo.setEarliestBegindate(row == null ? null : toLocalDate(row.get("earliestBegindate")));
            vo.setLastBegindate(row == null ? null : toLocalDate(row.get("lastBegindate")));

            int total = vo.getTotalPlot() == null ? 0 : vo.getTotalPlot();
            if (total > 0) {
                vo.setCompletionRate(BigDecimal.valueOf(finished)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP));
            } else {
                vo.setCompletionRate(BigDecimal.ZERO);
            }
        }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(v.toString());
    }

    private int toInt(Object v) {
        return v == null ? 0 : ((Number) v).intValue();
    }

    private LocalDate toLocalDate(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDate ld) {
            return ld;
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (v instanceof java.util.Date d) {
            return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        return LocalDate.parse(v.toString());
    }

    private void enrichDetails(List<PlantDetailsVo> details) {
        if (CollUtil.isEmpty(details)) {
            return;
        }
        Set<Long> plotIds = details.stream().map(PlantDetailsVo::getPlotId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> cropIds = details.stream().map(PlantDetailsVo::getCropId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> teamIds = new java.util.HashSet<>();
        details.forEach(d -> {
            if (d.getPlantBy() != null) {
                teamIds.add(d.getPlantBy());
            }
            if (d.getHarvestBy() != null) {
                teamIds.add(d.getHarvestBy());
            }
        });

        Map<Long, PlotInfo> plotMap = plotIds.isEmpty() ? Map.of() : plotMapper.selectByIds(plotIds).stream()
            .collect(Collectors.toMap(PlotInfo::getId, p -> p, (a, b) -> a));
        Map<Long, String> cropMap = cropIds.isEmpty() ? Map.of() : cropMapper.selectByIds(cropIds).stream()
            .collect(Collectors.toMap(CropInfo::getId, CropInfo::getCropName, (a, b) -> a));
        Map<Long, String> teamMap = teamIds.isEmpty() ? Map.of() : teamMapper.selectByIds(teamIds).stream()
            .collect(Collectors.toMap(PlantWorkTeam::getId, PlantWorkTeam::getTeamName, (a, b) -> a));

        for (PlantDetailsVo d : details) {
            PlotInfo plot = plotMap.get(d.getPlotId());
            if (plot != null) {
                d.setPlotName(plot.getPlotName());
                d.setPlotCode(plot.getPlotCode());
            }
            d.setCropName(cropMap.get(d.getCropId()));
            d.setPlantTeamName(d.getPlantBy() == null ? null : teamMap.get(d.getPlantBy()));
            d.setHarvestTeamName(d.getHarvestBy() == null ? null : teamMap.get(d.getHarvestBy()));
        }
    }

    // ============================================================
    // 创建：3 步向导提交
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createByBo(PlantPlanCreateBo bo) {
        CropInfo crop = cropMapper.selectById(bo.getCropId());
        if (crop == null) {
            throw new ServiceException("作物不存在：" + bo.getCropId());
        }
        if (crop.getMinCycle() == null || crop.getMaxCycle() == null) {
            throw new ServiceException("作物 min_cycle / max_cycle 未配置，无法生成派生字段：" + crop.getCropName());
        }
        // 批量查地块（一次 IN 查询）
        Set<Long> plotIds = bo.getDetails().stream()
            .map(PlantDetailInputBo::getPlotId).collect(Collectors.toSet());
        Map<Long, PlotInfo> plotMap = plotMapper.selectByIds(plotIds).stream()
            .collect(Collectors.toMap(PlotInfo::getId, p -> p));
        for (Long pid : plotIds) {
            if (!plotMap.containsKey(pid)) {
                throw new ServiceException("地块不存在：" + pid);
            }
        }

        // 主表 INSERT
        PlantPlan plan = new PlantPlan();
        plan.setPlanNo(nextPlanNo());
        plan.setPlanYear(bo.getPlanYear());
        plan.setPlanSeason(bo.getPlanSeason());
        plan.setCropId(bo.getCropId());
        plan.setPlantDate(bo.getPlantDate());
        plan.setPlantStatus("pending");
        baseMapper.insert(plan);

        // 明细 INSERT（逐行 INSERT；BaseMapperPlus 没有 insertBatchSomeColumn 时按单条做）
        List<PlantDetails> detailEntities = new ArrayList<>(bo.getDetails().size());
        for (PlantDetailInputBo item : bo.getDetails()) {
            PlotInfo plot = plotMap.get(item.getPlotId());
            PlantDetails detail = buildDetail(plan.getId(), plan.getCropId(), crop, plot, item, bo.getPlanYear());
            detailEntities.add(detail);
        }
        for (PlantDetails d : detailEntities) {
            detailsMapper.insert(d);
        }

        baseMapper.recalcAggregates(plan.getId());
        return plan.getId();
    }

    private PlantDetails buildDetail(Long planId, Long cropId, CropInfo crop, PlotInfo plot,
                                     PlantDetailInputBo input, int planYear) {
        LocalDate plantStart = LocalDate.of(planYear, input.getPlantMonth(), periodToDay(input.getPlantPeriod()));
        LocalDate earliest = plantStart.plusDays(crop.getMinCycle());
        LocalDate last = plantStart.plusDays(crop.getMaxCycle());
        BigDecimal expected = BigDecimal.ZERO;
        if (plot.getPlotArea() != null && crop.getPredictedPer() != null) {
            expected = plot.getPlotArea().multiply(crop.getPredictedPer());
        }
        PlantDetails d = new PlantDetails();
        d.setPlantId(planId);
        d.setPlotId(plot.getId());
        d.setCropId(cropId);
        d.setPlantMonth(input.getPlantMonth());
        d.setPlantPeriod(input.getPlantPeriod());
        d.setEarliestHarvestdate(earliest);
        d.setLastHarvestdate(last);
        d.setPlantStatus("pending");
        d.setHarvestStatus("pending");
        d.setPlotArea(plot.getPlotArea());
        d.setExpectedYield(expected);
        d.setIsPick(2);
        d.setPlantBy(input.getPlantBy());
        d.setHarvestBy(input.getHarvestBy());
        return d;
    }

    /**
     * 阶段串 → 该旬代表日（5/15/25），用于按月按旬拼真实日期。
     */
    private int periodToDay(String period) {
        return switch (period) {
            case "05" -> 5;
            case "15" -> 15;
            case "25" -> 25;
            default -> throw new ServiceException("非法 plant_period（必须 05/15/25）：" + period);
        };
    }

    /**
     * 生成下一个 plan_no：{@code PLAN-yyyy-NNN}，按年序号递增。
     *
     * <p>D9 closing Group B 迁入 {@link IBizCodeGenerator}（BizCodeType.PLAN_NO，
     * seed 在 V202606071600，{@code daily_reset=2} 按年重置）—— Redisson 分布式锁 +
     * 序号表 UNIQUE 双保护，取代原 SELECT MAX inline 实现。</p>
     *
     * <p>语义注意：编号年取 {@code LocalDate.now().year}，业务字段 {@code plan_year} 独立保留
     * （用户可提前规划下一年，编号仍以创建时刻年份）。</p>
     */
    String nextPlanNo() {
        return bizCodeGenerator.generate(BizCodeType.PLAN_NO, Map.of());
    }

    // ============================================================
    // 编辑
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PlantPlanUpdateBo bo) {
        PlantPlan existing = baseMapper.selectById(bo.getId());
        if (existing == null) {
            throw new ServiceException("种植计划不存在或已删除：" + bo.getId());
        }

        // 业务规则：ongoing 不允许改作物
        if ("ongoing".equals(existing.getPlantStatus())
            && bo.getCropId() != null
            && !bo.getCropId().equals(existing.getCropId())) {
            throw new ServiceException("已开始执行的计划不允许修改作物");
        }

        // 主表字段更新
        if (StringUtils.isNotBlank(bo.getPlanSeason())) {
            existing.setPlanSeason(bo.getPlanSeason());
        }
        if (bo.getCropId() != null) {
            existing.setCropId(bo.getCropId());
        }
        if (bo.getPlantDate() != null) {
            existing.setPlantDate(bo.getPlantDate());
        }
        baseMapper.updateById(existing);

        // 明细 diff
        if (bo.getDetails() != null) {
            applyDetailsDiff(existing, bo.getDetails());
            baseMapper.recalcAggregates(existing.getId());
        }
        return 1;
    }

    private void applyDetailsDiff(PlantPlan plan, List<PlantDetailInputBo> newList) {
        CropInfo crop = cropMapper.selectById(plan.getCropId());
        if (crop == null) {
            throw new ServiceException("作物不存在：" + plan.getCropId());
        }
        // 查现有明细
        List<PlantDetails> existing = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>().eq(PlantDetails::getPlantId, plan.getId()));
        Map<Long, PlantDetails> existingMap = existing.stream()
            .collect(Collectors.toMap(PlantDetails::getId, d -> d));

        // 新 list 中 id 集合
        Set<Long> incomingIds = newList.stream()
            .map(PlantDetailInputBo::getId).filter(Objects::nonNull).collect(Collectors.toSet());

        // 删除 = 旧有 - 新无
        List<Long> toDelete = new ArrayList<>();
        for (PlantDetails old : existing) {
            if (!incomingIds.contains(old.getId())) {
                if (old.getBeginActualdate() != null) {
                    throw new ServiceException("明细已开始执行，无法删除（地块 plot_id=" + old.getPlotId() + "）");
                }
                toDelete.add(old.getId());
            }
        }
        if (!toDelete.isEmpty()) {
            // 软删走基类
            softDelete(toDelete);
        }

        // 处理 INSERT / UPDATE
        Set<Long> needPlotIds = newList.stream().map(PlantDetailInputBo::getPlotId).collect(Collectors.toSet());
        Map<Long, PlotInfo> plotMap = needPlotIds.isEmpty()
            ? Map.of()
            : plotMapper.selectByIds(needPlotIds).stream().collect(Collectors.toMap(PlotInfo::getId, p -> p));

        for (PlantDetailInputBo input : newList) {
            PlotInfo plot = plotMap.get(input.getPlotId());
            if (plot == null) {
                throw new ServiceException("地块不存在：" + input.getPlotId());
            }
            if (input.getId() == null) {
                // INSERT
                PlantDetails d = buildDetail(plan.getId(), plan.getCropId(), crop, plot, input, plan.getPlanYear());
                detailsMapper.insert(d);
            } else {
                // UPDATE
                PlantDetails old = existingMap.get(input.getId());
                if (old == null) {
                    throw new ServiceException("明细不存在或已删除：" + input.getId());
                }
                if (old.getBeginActualdate() != null) {
                    // 已开始：只允许改班组
                    PlantDetails patch = new PlantDetails();
                    patch.setId(old.getId());
                    patch.setPlantBy(input.getPlantBy());
                    patch.setHarvestBy(input.getHarvestBy());
                    detailsMapper.updateById(patch);
                } else {
                    // 未开始：plot/month/period 可改，重算派生字段
                    PlantDetails refreshed = buildDetail(plan.getId(), plan.getCropId(), crop, plot, input, plan.getPlanYear());
                    refreshed.setId(old.getId());
                    detailsMapper.updateById(refreshed);
                }
            }
        }
    }

    // ============================================================
    // 软删
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return 0;
        }
        // 校验：每个 plan 的 details 必须全部 begin_actualdate IS NULL
        Long started = detailsMapper.selectCount(new LambdaQueryWrapper<PlantDetails>()
            .in(PlantDetails::getPlantId, ids)
            .isNotNull(PlantDetails::getBeginActualdate));
        if (started != null && started > 0) {
            throw new ServiceException("计划已有明细开始执行，无法删除");
        }
        // 先软删明细，再软删主表
        List<PlantDetails> details = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>().in(PlantDetails::getPlantId, ids));
        if (!details.isEmpty()) {
            List<Long> detailIds = details.stream().map(PlantDetails::getId).toList();
            softDeleteDetails(detailIds);
        }
        return softDelete(ids);
    }

    /**
     * 明细软删：基类 softDelete 是泛型 T = PlantPlan，明细需要独立实现。
     * 直接 wrapper-only update 模式与基类一致。
     */
    private void softDeleteDetails(Collection<Long> detailIds) {
        java.util.Date now = new java.util.Date();
        Long updateBy = currentUserIdSafe();
        for (Long id : detailIds) {
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<PlantDetails> wrapper =
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<PlantDetails>update()
                    .eq("id", id)
                    .eq("del_flag", "0")
                    .set("del_flag", "1")
                    .set("del_unique", id)
                    .set("update_by", updateBy)
                    .set("update_time", now);
            detailsMapper.update(null, wrapper);
        }
    }

    // ============================================================
    // 甘特图
    // ============================================================

    @Override
    public PlantPlanGanttVo getGantt(Long planId) {
        PlantPlan plan = baseMapper.selectById(planId);
        if (plan == null) {
            throw new ServiceException("种植计划不存在或已删除：" + planId);
        }
        CropInfo crop = cropMapper.selectById(plan.getCropId());
        List<PlantDetails> details = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>().eq(PlantDetails::getPlantId, planId));
        Set<Long> plotIds = details.stream().map(PlantDetails::getPlotId).collect(Collectors.toSet());
        Map<Long, PlotInfo> plotMap = plotIds.isEmpty()
            ? Map.of()
            : plotMapper.selectByIds(plotIds).stream().collect(Collectors.toMap(PlotInfo::getId, p -> p));

        PlantPlanGanttVo vo = new PlantPlanGanttVo();
        vo.setPlanId(plan.getId());
        vo.setPlanNo(plan.getPlanNo());
        vo.setPlanYear(plan.getPlanYear());
        vo.setPlanSeason(plan.getPlanSeason());
        vo.setCropName(crop == null ? null : crop.getCropName());
        List<PlantPlanGanttVo.Row> rows = details.stream().map(d -> {
            PlantPlanGanttVo.Row row = new PlantPlanGanttVo.Row();
            row.setDetailId(d.getId());
            row.setPlotId(d.getPlotId());
            PlotInfo plot = plotMap.get(d.getPlotId());
            if (plot != null) {
                row.setPlotName(plot.getPlotName());
                row.setPlotCode(plot.getPlotCode());
            }
            row.setEarliestHarvestdate(d.getEarliestHarvestdate());
            row.setLastHarvestdate(d.getLastHarvestdate());
            row.setBeginActualdate(d.getBeginActualdate());
            row.setEndActualdate(d.getEndActualdate());
            row.setBeginHarvestdate(d.getBeginHarvestdate());
            row.setEndHarvestdate(d.getEndHarvestdate());
            row.setPlantStatus(d.getPlantStatus());
            row.setHarvestStatus(d.getHarvestStatus());
            return row;
        }).collect(Collectors.toList());
        vo.setRows(rows);
        return vo;
    }

    // ============================================================
    // 可用地块（向导 step3）
    // ============================================================

    @Override
    public List<PlotByZoneVo> listAvailablePlots() {
        List<PlotZone> zones = zoneMapper.selectList(
            new LambdaQueryWrapper<PlotZone>().orderByAsc(PlotZone::getZoneCode));
        List<PlotInfo> plots = plotMapper.selectList(
            new LambdaQueryWrapper<PlotInfo>().orderByAsc(PlotInfo::getPlotCode));

        Map<Long, List<PlotInfo>> plotsByZone = plots.stream()
            .filter(p -> p.getZoneId() != null)
            .collect(Collectors.groupingBy(PlotInfo::getZoneId, LinkedHashMap::new, Collectors.toList()));

        // 没有 zone 的地块归到 zoneId=null 桶（前端单独展示）
        List<PlotInfo> orphan = plots.stream().filter(p -> p.getZoneId() == null).toList();

        List<PlotByZoneVo> result = new ArrayList<>(zones.size() + (orphan.isEmpty() ? 0 : 1));
        for (PlotZone z : zones) {
            PlotByZoneVo vo = new PlotByZoneVo();
            vo.setZoneId(z.getId());
            vo.setZoneName(z.getZoneName());
            vo.setZoneCode(z.getZoneCode());
            vo.setPlots(toPlotVoList(plotsByZone.getOrDefault(z.getId(), Collections.emptyList())));
            result.add(vo);
        }
        if (!orphan.isEmpty()) {
            PlotByZoneVo vo = new PlotByZoneVo();
            vo.setZoneId(null);
            vo.setZoneName("未分组");
            vo.setZoneCode("");
            vo.setPlots(toPlotVoList(orphan));
            result.add(vo);
        }
        return result;
    }

    private List<PlotByZoneVo.Plot> toPlotVoList(List<PlotInfo> plots) {
        List<PlotByZoneVo.Plot> list = new ArrayList<>(plots.size());
        for (PlotInfo p : plots) {
            PlotByZoneVo.Plot pv = new PlotByZoneVo.Plot();
            pv.setPlotId(p.getId());
            pv.setPlotName(p.getPlotName());
            pv.setPlotCode(p.getPlotCode());
            pv.setPlotArea(p.getPlotArea());
            pv.setPlotStatus(p.getPlotStatus());
            list.add(pv);
        }
        return list;
    }

    /** 供测试构造时使用（避免单测加 jpa Suppress）。 */
    Map<String, Object> debugInfo(Long planId) {
        Map<String, Object> m = new HashMap<>();
        m.put("planId", planId);
        return m;
    }

    // ============================================================
    // mp 开始种植开工（FIX-PLT-MP-SEED-001 #5）
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int startPlant(PlantStartBo bo) {
        if (bo == null || CollUtil.isEmpty(bo.getDetailIds())) {
            return 0;
        }
        // 查目标明细（基类查询自动带 del_flag + V1 单租户）；仅保留尚未开工(begin_actualdate IS NULL)的明细
        List<PlantDetails> details = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .in(PlantDetails::getId, bo.getDetailIds()));
        // 校验：传入的 detailIds 必须全部存在（属当前租户）；缺失即跨租户 / 已删除 / 非法 id
        if (details.size() != new java.util.HashSet<>(bo.getDetailIds()).size()) {
            throw new ServiceException("部分计划地块不存在或无权操作，请刷新后重试");
        }
        List<Long> startableDetailIds = details.stream()
            .filter(d -> d.getBeginActualdate() == null)
            .map(PlantDetails::getId)
            .toList();
        if (startableDetailIds.isEmpty()) {
            // 所选明细均已开工：幂等返 0（非错误，前端按 0 行提示"已开工"）
            return 0;
        }

        // 批量回写明细：begin_actualdate + plant_by + plant_status='ongoing'
        Long updateBy = currentUserIdSafe();
        detailsMapper.update(null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PlantDetails>()
                .in(PlantDetails::getId, startableDetailIds)
                .set(PlantDetails::getBeginActualdate, bo.getBeginActualdate())
                .set(PlantDetails::getPlantBy, bo.getPlantBy())
                .set(PlantDetails::getPlantStatus, "ongoing")
                .set(PlantDetails::getUpdateBy, updateBy));

        // 同步关联地块 plot_status=2（种植中）
        Set<Long> plotIds = details.stream()
            .filter(d -> startableDetailIds.contains(d.getId()))
            .map(PlantDetails::getPlotId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!plotIds.isEmpty()) {
            plotMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PlotInfo>()
                    .in(PlotInfo::getId, plotIds)
                    .set(PlotInfo::getPlotStatus, 2)
                    .set(PlotInfo::getUpdateBy, updateBy));
        }
        return startableDetailIds.size();
    }

    // ============================================================
    // mp 种植完成收工
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int finishPlant(PlantFinishBo bo) {
        if (bo == null || CollUtil.isEmpty(bo.getDetailIds())) {
            return 0;
        }
        // 查目标明细（基类查询自动带 del_flag + V1 单租户）
        List<PlantDetails> details = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .in(PlantDetails::getId, bo.getDetailIds()));
        // 校验：传入的 detailIds 必须全部存在（属当前租户）；缺失即跨租户 / 已删除 / 非法 id
        if (details.size() != new java.util.HashSet<>(bo.getDetailIds()).size()) {
            throw new ServiceException("部分计划地块不存在或无权操作，请刷新后重试");
        }
        // 仅保留进行中（plant_status='ongoing' + 已开工 + 未完成）的明细可被完成
        List<Long> finishableDetailIds = details.stream()
            .filter(d -> "ongoing".equals(d.getPlantStatus())
                && d.getBeginActualdate() != null
                && d.getEndActualdate() == null)
            .map(PlantDetails::getId)
            .toList();
        if (finishableDetailIds.isEmpty()) {
            // 所选明细均非进行中 / 已完成：幂等返 0（非错误，前端按 0 行提示"已完成"）
            return 0;
        }

        // 批量回写明细：plant_status='completed' + end_actualdate；不动地块 plot_status（保持 2 种植）
        Long updateBy = currentUserIdSafe();
        detailsMapper.update(null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PlantDetails>()
                .in(PlantDetails::getId, finishableDetailIds)
                .set(PlantDetails::getPlantStatus, "completed")
                .set(PlantDetails::getEndActualdate, bo.getEndActualdate())
                .set(PlantDetails::getUpdateBy, updateBy));
        return finishableDetailIds.size();
    }

    @Override
    public PlantPlanSummaryVo aggregateForDemandSummary() {
        PlantPlanSummaryVo vo = baseMapper.selectDemandSummary();
        if (vo == null) {
            vo = new PlantPlanSummaryVo();
        }
        if (vo.getPlotCount() == null) {
            vo.setPlotCount(0);
        }
        if (vo.getExpectedYieldKg() == null) {
            vo.setExpectedYieldKg(BigDecimal.ZERO);
        }
        return vo;
    }

    // ============================================================
    // 列表顶部 5 KPI 卡（FIX-PLT-AD-PLAN-001）
    // ============================================================

    @Override
    public PlantPlanStatsVo getPlanStats() {
        PlantPlanStatsVo vo = new PlantPlanStatsVo();

        Map<String, Object> plotStats = baseMapper.selectPlotStatusStats();
        vo.setIdlePlot(plotStats == null ? 0 : toInt(plotStats.get("idlePlot")));
        vo.setPlantedPlot(plotStats == null ? 0 : toInt(plotStats.get("plantedPlot")));

        Map<String, Object> planStats = baseMapper.selectPlanDetailStats();
        int plannedPlot = planStats == null ? 0 : toInt(planStats.get("plannedPlot"));
        int detailRows = planStats == null ? 0 : toInt(planStats.get("detailRows"));
        int cropVariety = planStats == null ? 0 : toInt(planStats.get("cropVarietyCount"));
        vo.setPlannedPlot(plannedPlot);
        vo.setCropVarietyCount(cropVariety);
        if (plannedPlot > 0) {
            vo.setPlotUsageFreq(BigDecimal.valueOf(detailRows)
                .divide(BigDecimal.valueOf(plannedPlot), 1, RoundingMode.HALF_UP));
        } else {
            vo.setPlotUsageFreq(BigDecimal.ZERO);
        }
        return vo;
    }
}
