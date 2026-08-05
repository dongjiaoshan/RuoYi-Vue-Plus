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
import org.dromara.djs.plant.plan.domain.vo.PlotYearPlanRowVo;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plan.mapper.PlantPlanMapper;
import org.dromara.djs.plant.plan.service.IPlantPlanService;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.dromara.djs.plant.team.service.PlantTeamLinkService;
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
import java.util.stream.IntStream;

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
    private final PlantTeamLinkService teamLinkService;

    public PlantPlanServiceImpl(
        PlantPlanMapper baseMapper,
        PlantDetailsMapper detailsMapper,
        CropInfoMapper cropMapper,
        PlotInfoMapper plotMapper,
        PlotZoneMapper zoneMapper,
        PlantWorkTeamMapper teamMapper,
        IBizCodeGenerator bizCodeGenerator,
        PlantTeamLinkService teamLinkService) {
        super(baseMapper);
        this.detailsMapper = detailsMapper;
        this.cropMapper = cropMapper;
        this.plotMapper = plotMapper;
        this.zoneMapper = zoneMapper;
        this.teamMapper = teamMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.teamLinkService = teamLinkService;
    }

    /**
     * 班组多选取值：list 非空 → 去空去重原样返回；否则回落单值（非空→单元素）；再否则空 list。
     */
    private List<Long> effectiveTeamIds(List<Long> ids, Long single) {
        if (CollUtil.isNotEmpty(ids)) {
            return ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        }
        return single == null ? Collections.emptyList() : List.of(single);
    }

    /** 取多选第一个（写旧单列过渡兼容）；空 → null。 */
    private Long firstTeamId(List<Long> effectiveIds) {
        return CollUtil.isEmpty(effectiveIds) ? null : effectiveIds.get(0);
    }

    // ============================================================
    // 查询
    // ============================================================

    @Override
    public TableDataInfo<PlantPlanVo> queryPageList(PlantPlanQuery query, PageQuery pageQuery) {
        Page<PlantPlanVo> page = baseMapper.selectVoPage(buildFixedOrderPage(pageQuery), buildWrapper(query));
        enrichCrop(page.getRecords());
        enrichAggregates(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 分页对象：丢掉请求参数 {@code orderByColumn}/{@code isAsc} 带来的排序项，只认
     * {@link #applyPlantingPeriodOrder} 定的固定排序。
     *
     * <p>MP 的分页拦截器把 {@code PageQuery} 的排序项**拼在 wrapper 的 ORDER BY 之前**
     * （{@code ORDER BY <请求参数> , plan_year DESC, …}），所以 URL 上随便带个
     * {@code ?orderByColumn=planYear&isAsc=asc} 就能静默压过甲方要求的首排序键，
     * 而且不报错、不留痕。本页原型没有可点排序的列，前端也不发这两个参数，
     * 这里直接清掉，把「按计划种植日期降序」变成服务端保证而不是前端自觉。</p>
     *
     * <p>将来真要做点表头排序，删掉这个方法改回 {@code pageQuery.build()} 即可。</p>
     */
    private <T> Page<T> buildFixedOrderPage(PageQuery pageQuery) {
        Page<T> page = pageQuery.build();
        page.orders().clear();
        return page;
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
            applyPlantingPeriodOrder(wrapper);
            return wrapper;
        }
        wrapper.like(StringUtils.isNotBlank(query.getPlanNo()), PlantPlan::getPlanNo, query.getPlanNo())
            .eq(query.getPlanYear() != null, PlantPlan::getPlanYear, query.getPlanYear())
            .eq(StringUtils.isNotBlank(query.getPlanSeason()), PlantPlan::getPlanSeason, query.getPlanSeason())
            .eq(query.getCropId() != null, PlantPlan::getCropId, query.getCropId())
            .eq(StringUtils.isNotBlank(query.getPlantStatus()), PlantPlan::getPlantStatus, query.getPlantStatus())
            .eq(query.getQueryCreateBy() != null, PlantPlan::getCreateBy, query.getQueryCreateBy())
            .apply(query.getQueryUpdateTime() != null, "DATE(update_time) = {0}", query.getQueryUpdateTime());
        // 农作物名称模糊：crop_id 落在 crop_name LIKE 的作物集合内（crop_name 在 t_plant_crop_info，非主表列）
        if (StringUtils.isNotBlank(query.getCropName())) {
            wrapper.apply(
                "crop_id IN (SELECT id FROM t_plant_crop_info WHERE del_flag = '0' AND crop_name LIKE {0})",
                "%" + query.getCropName().trim() + "%");
        }
        // 计划编制人姓名模糊：create_by 落在 nick_name LIKE 的用户集合内（与列表 createByName 同源 sys_user.nick_name）
        if (StringUtils.isNotBlank(query.getQueryCreateByName())) {
            wrapper.apply(
                "create_by IN (SELECT user_id FROM sys_user WHERE nick_name LIKE {0})",
                "%" + query.getQueryCreateByName().trim() + "%");
        }
        applyPlanDateRange(wrapper, query.getBeginPlanDate(), query.getEndPlanDate());
        applyPlanMonths(wrapper, query.getPlanMonths());
        applyPlantingPeriodOrder(wrapper);
        return wrapper;
    }

    /**
     * 计划月份多选过滤（admin row173）：该计划存在 plant_month 落在所选月份集合内的种植明细。
     *
     * <p>plant_month 在 t_plant_plant_details（非主表列），故用相关子查询
     * {@code EXISTS(... AND d.plant_month IN (...))}；年份维度由 {@code planYear} 等值单独过滤。
     * 月份是 Integer，占位符 {@code {0},{1}...} 交给 MP 预编译，无拼接注入面；
     * null / 越界（非 1-12）月份直接丢弃，全丢完等于没选、不加条件。</p>
     */
    private void applyPlanMonths(LambdaQueryWrapper<PlantPlan> wrapper, List<Integer> months) {
        if (CollUtil.isEmpty(months)) {
            return;
        }
        List<Integer> valid = months.stream()
            .filter(Objects::nonNull)
            .filter(m -> m >= 1 && m <= 12)
            .distinct()
            .toList();
        if (valid.isEmpty()) {
            return;
        }
        String placeholders = IntStream.range(0, valid.size())
            .mapToObj(i -> "{" + i + "}")
            .collect(Collectors.joining(", "));
        wrapper.apply(
            "EXISTS (SELECT 1 FROM t_plant_plant_details d "
                + "WHERE d.plant_id = t_plant_plant_plan.id AND d.del_flag = '0' "
                + "AND d.plant_month IN (" + placeholders + "))",
            valid.toArray());
    }

    /**
     * 列表排序（admin row173）：按「计划种植日期」降序 = 计划年份 → 计划月份 → 旬别（下旬→中旬→上旬）。
     *
     * <p>「计划种植日期」是明细派生值，列表列取最早开工那条明细的 月份+旬别（见
     * {@code PlantPlanMapper.selectListAggregates} 的 plantMonth/plantPeriod），非主表存储列，
     * 故排序键用同源的相关子查询
     * {@code MIN(CONCAT(LPAD(plant_month,2,'0'), plant_period))}（月份补零后拼旬别，字典序 == 时间序）。
     * plant_period 取值 05/15/25，DESC 恰好落成 下旬(25) → 中旬(15) → 上旬(05)。</p>
     *
     * <p>无明细的计划子查询返 NULL，MySQL DESC 下 NULL 排最后；末尾再按 id DESC 保证同键行的
     * 分页顺序稳定（否则翻页可能重复/漏行）。用 {@code last} 而非 {@code orderByDesc} 是因为
     * Lambda 写法表达不了子查询表达式；本方法在 buildWrapper 末尾唯一调用一次。</p>
     *
     * <p>⚠️ 子查询必须自己写 {@code d.tenant_id = t_plant_plant_plan.tenant_id}：MP 的
     * {@code TenantLineInnerInterceptor} 只给 WHERE 里的子查询注入租户条件，**不进 ORDER BY 的标量子查询**。
     * 缺这个谓词一是丢租户隔离，二是 {@code t_plant_plant_details} 的索引
     * （{@code uk_plan_plot_month(tenant_id,plant_id,…)} / {@code idx_plan(tenant_id,plant_id)}）
     * 首列就是 tenant_id，最左前缀失效会退化成全表扫（EXPLAIN type=ALL），
     * 而本排序键是逐行标量子查询，成本 = 计划数 × 明细全表，数据量上去直接卡死。
     * 关联外层 tenant_id 而不是硬编码 '1001'，V2 开多租户不用回来改。</p>
     */
    private void applyPlantingPeriodOrder(LambdaQueryWrapper<PlantPlan> wrapper) {
        wrapper.last("ORDER BY plan_year DESC, ("
            + "SELECT MIN(CONCAT(LPAD(d.plant_month, 2, '0'), d.plant_period)) "
            + "FROM t_plant_plant_details d "
            + "WHERE d.plant_id = t_plant_plant_plan.id AND d.del_flag = '0' "
            + "AND d.tenant_id = t_plant_plant_plan.tenant_id"
            + ") DESC, id DESC");
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
     * 按 cropId 批量 enrich {@code cropName} + {@code cropImage}。
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
                vo.setCropImage(resolveCropImageOssId(crop));
            }
        }
    }

    /**
     * 取作物图 ossId：{@code image_oss_id → crop_image_preview → crop_image_url 首段} 兜底，全空返 null。
     *
     * <p>三个字段各有来源——{@code image_oss_id} 图库自动匹配 / 手选写入，
     * {@code crop_image_preview} 与 {@code crop_image_url}（逗号分隔多图，取第一张）是 admin 端用户上传落位。
     * 只读单个字段会漏掉另两路的图，表现为列表「作物图片」整列显 {@code -}。</p>
     */
    private String resolveCropImageOssId(CropInfo c) {
        if (StringUtils.isNotBlank(c.getImageOssId())) {
            return c.getImageOssId();
        }
        if (StringUtils.isNotBlank(c.getCropImagePreview())) {
            return c.getCropImagePreview();
        }
        if (StringUtils.isNotBlank(c.getCropImageUrl())) {
            String first = c.getCropImageUrl().split(",")[0].trim();
            return StringUtils.isBlank(first) ? null : first;
        }
        return null;
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
            vo.setPlantMonth(row == null ? null : toIntOrNull(row.get("plantMonth")));
            vo.setPlantPeriod(row == null ? null : (row.get("plantPeriod") == null ? null : row.get("plantPeriod").toString()));

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

    private Integer toIntOrNull(Object v) {
        return v == null ? null : ((Number) v).intValue();
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

        // 班组多选中间表 enrich（G1-TEAMS-MULTISELECT，row36）：批量取各明细 role → 名/id
        Set<Long> detailIds = details.stream().map(PlantDetailsVo::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Map<String, List<String>>> namesMap = teamLinkService.detailTeamNames(detailIds);
        Map<Long, Map<String, List<Long>>> idsMap = teamLinkService.detailTeamIds(detailIds);

        for (PlantDetailsVo d : details) {
            PlotInfo plot = plotMap.get(d.getPlotId());
            if (plot != null) {
                d.setPlotName(plot.getPlotName());
                d.setPlotCode(plot.getPlotCode());
            }
            d.setCropName(cropMap.get(d.getCropId()));
            d.setPlantTeamName(d.getPlantBy() == null ? null : teamMap.get(d.getPlantBy()));
            d.setHarvestTeamName(d.getHarvestBy() == null ? null : teamMap.get(d.getHarvestBy()));

            Map<String, List<String>> roleNames = namesMap.getOrDefault(d.getId(), Map.of());
            Map<String, List<Long>> roleIds = idsMap.getOrDefault(d.getId(), Map.of());
            List<String> plantNames = roleNames.get(PlantTeamLinkService.ROLE_PLANT);
            List<String> harvestNames = roleNames.get(PlantTeamLinkService.ROLE_HARVEST);
            // 中间表无数据（历史行未回填 / 单值兜底）时回落旧单列名，保证展示不空
            d.setPlantTeamNames(CollUtil.isNotEmpty(plantNames) ? plantNames
                : (d.getPlantTeamName() == null ? Collections.emptyList() : List.of(d.getPlantTeamName())));
            d.setHarvestTeamNames(CollUtil.isNotEmpty(harvestNames) ? harvestNames
                : (d.getHarvestTeamName() == null ? Collections.emptyList() : List.of(d.getHarvestTeamName())));
            List<Long> plantIds = roleIds.get(PlantTeamLinkService.ROLE_PLANT);
            List<Long> harvestIds = roleIds.get(PlantTeamLinkService.ROLE_HARVEST);
            d.setPlantByIds(CollUtil.isNotEmpty(plantIds) ? plantIds
                : (d.getPlantBy() == null ? Collections.emptyList() : List.of(d.getPlantBy())));
            d.setHarvestByIds(CollUtil.isNotEmpty(harvestIds) ? harvestIds
                : (d.getHarvestBy() == null ? Collections.emptyList() : List.of(d.getHarvestBy())));
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
        // 采摘区间交叉校验：同一地块已有种植明细的采摘区间不可与本次重叠
        validateHarvestOverlap(detailEntities, plotMap, null);
        List<PlantDetailInputBo> inputs = bo.getDetails();
        for (int i = 0; i < detailEntities.size(); i++) {
            PlantDetails d = detailEntities.get(i);
            detailsMapper.insert(d);
            syncDetailTeamLinks(d.getId(), inputs.get(i));   // 班组多选中间表
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
        // 旧单列 = 多选第一个（过渡兼容 + 下游单值聚合口径不变）；全集在 insert 后 sync 中间表
        d.setPlantBy(firstTeamId(effectiveTeamIds(input.getPlantByIds(), input.getPlantBy())));
        d.setHarvestBy(firstTeamId(effectiveTeamIds(input.getHarvestByIds(), input.getHarvestBy())));
        return d;
    }

    /**
     * 明细班组多选中间表同步（G1-TEAMS-MULTISELECT，row36）：种植 role=plant / 采摘 role=harvest。
     * BO list 为 null 时退化为旧单列单值；两者皆空则清空该角色关联。
     */
    private void syncDetailTeamLinks(Long detailId, PlantDetailInputBo input) {
        teamLinkService.syncDetailTeams(detailId, PlantTeamLinkService.ROLE_PLANT,
            effectiveTeamIds(input.getPlantByIds(), input.getPlantBy()));
        teamLinkService.syncDetailTeams(detailId, PlantTeamLinkService.ROLE_HARVEST,
            effectiveTeamIds(input.getHarvestByIds(), input.getHarvestBy()));
    }

    /**
     * 采摘区间交叉校验：本次提交的每个明细，按地块比对同地块已有明细的采摘区间
     * （{@code earliest_harvestdate ~ last_harvestdate}），若日期区间重叠则阻止提交。
     *
     * <p>重叠判定：两区间 {@code [a1,a2]} 与 {@code [b1,b2]} 重叠 ⇔ {@code a1 <= b2 && a2 >= b1}（含端点）。
     * 同一地块上一茬采摘尚未结束、下一茬采摘已开始即视为冲突，避免地块采摘期撞车。</p>
     *
     * <p>{@code excludePlanId} 非空时排除该计划自身的已有明细（编辑场景：本计划明细将被整体替换，
     * 不应与"旧的自己"判冲突）。</p>
     *
     * @param incoming      本次待落库的明细（已算好 earliest/last harvestdate）
     * @param plotMap       地块 id → 地块（用于错误信息展示地块名）
     * @param excludePlanId 需排除的计划 id（编辑场景传本计划 id；新建传 null）
     */
    private void validateHarvestOverlap(List<PlantDetails> incoming,
                                        Map<Long, PlotInfo> plotMap,
                                        Long excludePlanId) {
        validateHarvestOverlap(incoming, plotMap, excludePlanId, null);
    }

    /**
     * 采摘区间重叠校验。{@code scene} 非空时作为异常前缀，标明冲突发生的场景（如移栽 100% 自动落地时
     * 物化「转移目标地块」入计划），避免用户误以为冒出一块无关地块（row159）。地块名优先用地块编号
     * {@code plotCode}（与 mp 地块选择器展示一致），回落地块名 / id。
     */
    private void validateHarvestOverlap(List<PlantDetails> incoming,
                                        Map<Long, PlotInfo> plotMap,
                                        Long excludePlanId,
                                        String scene) {
        Set<Long> plotIds = incoming.stream()
            .map(PlantDetails::getPlotId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (plotIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<PlantDetails> lqw = new LambdaQueryWrapper<PlantDetails>()
            .in(PlantDetails::getPlotId, plotIds)
            .isNotNull(PlantDetails::getEarliestHarvestdate)
            .isNotNull(PlantDetails::getLastHarvestdate)
            // 已采摘完成（含退茬）的历史茬口 = 地块已释放为空地，不再参与采摘期重叠校验，
            // 允许在其上继续种下一茬（harvest_status='completed' 表示该茬采收结束、地块空闲）。
            // 仍在种/采（pending / picking）的茬口保留冲突防护。
            .ne(PlantDetails::getHarvestStatus, "completed");
        if (excludePlanId != null) {
            lqw.ne(PlantDetails::getPlantId, excludePlanId);
        }
        List<PlantDetails> existing = detailsMapper.selectList(lqw);
        if (existing.isEmpty()) {
            return;
        }
        Map<Long, List<PlantDetails>> existingByPlot = existing.stream()
            .collect(Collectors.groupingBy(PlantDetails::getPlotId));

        for (PlantDetails item : incoming) {
            LocalDate a1 = item.getEarliestHarvestdate();
            LocalDate a2 = item.getLastHarvestdate();
            if (a1 == null || a2 == null) {
                continue;
            }
            for (PlantDetails old : existingByPlot.getOrDefault(item.getPlotId(), Collections.emptyList())) {
                LocalDate b1 = old.getEarliestHarvestdate();
                LocalDate b2 = old.getLastHarvestdate();
                if (!a1.isAfter(b2) && !a2.isBefore(b1)) {
                    PlotInfo plot = plotMap == null ? null : plotMap.get(item.getPlotId());
                    String plotLabel = plot == null ? ("地块 " + item.getPlotId())
                        : (plot.getPlotCode() != null ? plot.getPlotCode()
                        : (plot.getPlotName() != null ? plot.getPlotName() : ("地块 " + item.getPlotId())));
                    String prefix = StringUtils.isNotBlank(scene) ? (scene + "：") : "";
                    throw new ServiceException(prefix + "「" + plotLabel + "」已有种植记录的采摘区间（"
                        + b1 + " ~ " + b2 + "）与本次采摘区间（" + a1 + " ~ " + a2
                        + "）重叠，请调整种植月份或旬别后再提交");
                }
            }
        }
    }

    // ============================================================
    // 育苗移栽落地（PLT-TRANSPLANT-REDO-001）
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int materializeTransplantTargets(Long planId, Long sourcePlotId, Long cropId, List<Long> targetPlotIds) {
        if (planId == null || sourcePlotId == null || cropId == null || CollUtil.isEmpty(targetPlotIds)) {
            return 0;
        }
        // 源育苗明细（日期模板）：同计划 + 同源地块 + 同作物；多条(多月旬)取 id 最小一条作日期模板
        List<PlantDetails> sourceDetails = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getPlantId, planId)
                .eq(PlantDetails::getPlotId, sourcePlotId)
                .eq(PlantDetails::getCropId, cropId)
                .orderByAsc(PlantDetails::getId));
        if (sourceDetails.isEmpty()) {
            throw new ServiceException("移栽源育苗明细不存在（计划 " + planId + " / 地块 " + sourcePlotId + "）");
        }
        PlantDetails src = sourceDetails.get(0);
        CropInfo crop = cropMapper.selectById(cropId);

        List<Long> distinctTargets = targetPlotIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, PlotInfo> targetMap = distinctTargets.isEmpty() ? Map.of()
            : plotMapper.selectByIds(distinctTargets).stream()
                .collect(Collectors.toMap(PlotInfo::getId, p -> p, (a, b) -> a));

        Long updateBy = currentUserIdSafe();
        List<PlantDetails> newDetails = new ArrayList<>();
        List<Long> newDetailPlotIds = new ArrayList<>();
        for (Long targetId : distinctTargets) {
            PlotInfo tp = targetMap.get(targetId);
            if (tp == null) {
                throw new ServiceException("移栽目标地块不存在：" + targetId);
            }
            // 幂等：目标已有本计划明细（前次落地 / 重复触发）→ 跳过，不重复建（并发双落地兜底）
            boolean exists = detailsMapper.selectCount(
                new LambdaQueryWrapper<PlantDetails>()
                    .eq(PlantDetails::getPlantId, planId)
                    .eq(PlantDetails::getPlotId, targetId)) > 0;
            if (exists) {
                continue;
            }
            PlantDetails d = new PlantDetails();
            d.setPlantId(planId);
            d.setPlotId(targetId);
            d.setCropId(cropId);
            d.setPlantMonth(src.getPlantMonth());
            d.setPlantPeriod(src.getPlantPeriod());
            d.setBeginActualdate(src.getBeginActualdate());          // 种植时间不变
            d.setEarliestHarvestdate(src.getEarliestHarvestdate());  // 采摘期含育苗时长，不变
            d.setLastHarvestdate(src.getLastHarvestdate());
            d.setPlantStatus("completed");   // 满种：已定植进产出期
            d.setHarvestStatus("pending");   // 采摘改在目标大田发生
            d.setPlotArea(tp.getPlotArea());
            BigDecimal expected = BigDecimal.ZERO;
            if (tp.getPlotArea() != null && crop != null && crop.getPredictedPer() != null) {
                expected = tp.getPlotArea().multiply(crop.getPredictedPer());
            }
            d.setExpectedYield(expected);
            d.setIsPick(2);
            d.setPlantBy(src.getPlantBy());   // 沿用源育苗种植班组（过渡兼容旧单列）
            d.setTransplantAdjusted(1);
            newDetails.add(d);
            newDetailPlotIds.add(targetId);
        }
        if (newDetails.isEmpty()) {
            return 0;   // 全部目标已落地（幂等）
        }
        // point③：目标须在计划时间窗口内无冲突计划（复用采摘区间重叠校验；有冲突则整事务回滚）。
        // row159：100% 落地时物化的是「源育苗地块的历史转移目标全集」，冲突地块可能非本次所选——
        // 传场景前缀让文案标明是「移栽落地·转移目标地块」，避免用户误以为报了无关地块。
        validateHarvestOverlap(newDetails, targetMap, null, "移栽落地·转移目标地块");
        for (PlantDetails d : newDetails) {
            detailsMapper.insert(d);
        }
        // 目标地块 plot_status=2（种植）
        plotMapper.update(null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PlotInfo>()
                .in(PlotInfo::getId, newDetailPlotIds)
                .set(PlotInfo::getPlotStatus, 2)
                .set(PlotInfo::getUpdateBy, updateBy));
        // 新增目标满种明细后主表 totalPlot / totalArea / earliest·last_harvestdate / plant_status 需重算
        baseMapper.recalcAggregates(planId);
        baseMapper.recalcPlanStatus(planId);
        return newDetails.size();
    }

    @Override
    public void validateTransplantTargetConflict(Long planId, Long sourcePlotId, Long cropId, Long targetPlotId) {
        if (planId == null || sourcePlotId == null || cropId == null || targetPlotId == null) {
            return;
        }
        // 目标已有本计划明细（已物化）→ 无需重复校验
        boolean exists = detailsMapper.selectCount(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getPlantId, planId)
                .eq(PlantDetails::getPlotId, targetPlotId)) > 0;
        if (exists) {
            return;
        }
        // 源育苗明细日期模板（同 materializeTransplantTargets：id 最小一条）
        List<PlantDetails> sourceDetails = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getPlantId, planId)
                .eq(PlantDetails::getPlotId, sourcePlotId)
                .eq(PlantDetails::getCropId, cropId)
                .orderByAsc(PlantDetails::getId));
        if (sourceDetails.isEmpty()) {
            return;   // 源缺失 → 落地环节会再报，选块阶段不阻断
        }
        PlantDetails src = sourceDetails.get(0);
        if (src.getEarliestHarvestdate() == null || src.getLastHarvestdate() == null) {
            return;
        }
        PlotInfo tp = plotMapper.selectById(targetPlotId);
        if (tp == null) {
            return;
        }
        PlantDetails prospective = new PlantDetails();
        prospective.setPlotId(targetPlotId);
        prospective.setEarliestHarvestdate(src.getEarliestHarvestdate());
        prospective.setLastHarvestdate(src.getLastHarvestdate());
        validateHarvestOverlap(List.of(prospective), Map.of(targetPlotId, tp), null, "移栽·转移目标地块");
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

        // 采摘区间交叉校验：先按本次提交算出各明细采摘区间（已开始执行的保持旧值），
        // 比对其它计划同地块已有采摘区间，重叠则阻止（排除本计划自身明细）
        List<PlantDetails> prospective = new ArrayList<>(newList.size());
        for (PlantDetailInputBo input : newList) {
            PlotInfo plot = plotMap.get(input.getPlotId());
            if (plot == null) {
                throw new ServiceException("地块不存在：" + input.getPlotId());
            }
            PlantDetails old = input.getId() == null ? null : existingMap.get(input.getId());
            if (old != null && old.getBeginActualdate() != null) {
                // 已开始：采摘区间不变，沿用旧值参与校验
                prospective.add(old);
            } else {
                prospective.add(buildDetail(plan.getId(), plan.getCropId(), crop, plot, input, plan.getPlanYear()));
            }
        }
        validateHarvestOverlap(prospective, plotMap, plan.getId());

        for (PlantDetailInputBo input : newList) {
            PlotInfo plot = plotMap.get(input.getPlotId());
            if (plot == null) {
                throw new ServiceException("地块不存在：" + input.getPlotId());
            }
            if (input.getId() == null) {
                // INSERT
                PlantDetails d = buildDetail(plan.getId(), plan.getCropId(), crop, plot, input, plan.getPlanYear());
                detailsMapper.insert(d);
                syncDetailTeamLinks(d.getId(), input);   // 班组多选中间表
            } else {
                // UPDATE
                PlantDetails old = existingMap.get(input.getId());
                if (old == null) {
                    throw new ServiceException("明细不存在或已删除：" + input.getId());
                }
                if (old.getBeginActualdate() != null) {
                    // 已开始：只允许改班组（旧单列 = 多选第一个）
                    PlantDetails patch = new PlantDetails();
                    patch.setId(old.getId());
                    patch.setPlantBy(firstTeamId(effectiveTeamIds(input.getPlantByIds(), input.getPlantBy())));
                    patch.setHarvestBy(firstTeamId(effectiveTeamIds(input.getHarvestByIds(), input.getHarvestBy())));
                    detailsMapper.updateById(patch);
                } else {
                    // 未开始：plot/month/period 可改，重算派生字段
                    PlantDetails refreshed = buildDetail(plan.getId(), plan.getCropId(), crop, plot, input, plan.getPlanYear());
                    refreshed.setId(old.getId());
                    detailsMapper.updateById(refreshed);
                }
                syncDetailTeamLinks(old.getId(), input);   // 班组多选中间表（先删后插，改班组即同步）
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removePlanDetail(Long detailId) {
        if (detailId == null) {
            throw new ServiceException("地块明细 id 为空");
        }
        PlantDetails detail = detailsMapper.selectById(detailId);
        if (detail == null) {
            throw new ServiceException("地块明细不存在或已删除：" + detailId);
        }
        // 只有「种植未开始」才可移出计划：plant_status=pending 且未落实际开始日期。
        // 已完成（completed）/ 进行中（ongoing）/ 已开工（begin_actualdate 非空）一律拒绝——
        // 这些明细下游已挂采摘计划、农事、产量等数据，删掉会留孤儿。
        if (!"pending".equals(detail.getPlantStatus()) || detail.getBeginActualdate() != null) {
            throw new ServiceException("该地块种植已开始或已完成，不能从计划中删除");
        }
        // 班组中间表按角色清空（传空集合 = 删掉该明细全部 link），避免留孤儿关联
        teamLinkService.syncDetailTeams(detailId, PlantTeamLinkService.ROLE_PLANT, Collections.emptyList());
        teamLinkService.syncDetailTeams(detailId, PlantTeamLinkService.ROLE_HARVEST, Collections.emptyList());
        softDeleteDetails(List.of(detailId));
        // 主表面积 / 地块数 / 采摘区间与派生状态随明细减少重算
        baseMapper.recalcAggregates(detail.getPlantId());
        baseMapper.recalcPlanStatus(detail.getPlantId());
        return 1;
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
    public List<PlotByZoneVo> listAvailablePlots(Integer planYear) {
        // 仅下发启用片区（zone_status=1=启用 / 0=停用，与片区数据实态 + admin toggle 同口径），停用片区不参与种植计划地块选择
        List<PlotZone> zones = zoneMapper.selectList(
            new LambdaQueryWrapper<PlotZone>()
                .eq(PlotZone::getZoneStatus, 1)
                .orderByAsc(PlotZone::getZoneCode));
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
        // planYear 非空：批量回填每块地的「当年轮作次数」（row27 子项1）
        fillRotationCount(result, planYear);
        return result;
    }

    /**
     * 批量回填各地块 rotationCount（当年该地块计划种植次数 = 当年种植明细行数）。
     * planYear 为空时不回填（保持 null，前端不渲染标签）；一次 GROUP BY 批量查避免 N+1。
     */
    private void fillRotationCount(List<PlotByZoneVo> zones, Integer planYear) {
        if (planYear == null || CollUtil.isEmpty(zones)) {
            return;
        }
        List<Long> plotIds = zones.stream()
            .flatMap(z -> z.getPlots() == null ? java.util.stream.Stream.empty() : z.getPlots().stream())
            .map(PlotByZoneVo.Plot::getPlotId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (plotIds.isEmpty()) {
            return;
        }
        Map<Long, Integer> countMap = detailsMapper.selectRotationCountByYear(plotIds, planYear).stream()
            .filter(row -> row.get("plotId") != null)
            .collect(Collectors.toMap(
                row -> ((Number) row.get("plotId")).longValue(),
                row -> toInt(row.get("rotationCount")),
                (a, b) -> a));
        for (PlotByZoneVo z : zones) {
            if (z.getPlots() == null) {
                continue;
            }
            for (PlotByZoneVo.Plot p : z.getPlots()) {
                p.setRotationCount(countMap.getOrDefault(p.getPlotId(), 0));
            }
        }
    }

    @Override
    public List<PlotYearPlanRowVo> getPlotYearPlanRows(Long plotId, Integer planYear) {
        if (plotId == null || planYear == null) {
            return Collections.emptyList();
        }
        return detailsMapper.selectPlotYearPlanRows(plotId, planYear);
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

        // 批量回写明细：begin_actualdate + plant_by（= 多选第一个，过渡兼容）+ plant_status='ongoing'
        Long updateBy = currentUserIdSafe();
        List<Long> effPlantTeams = effectiveTeamIds(bo.getPlantByIds(), bo.getPlantBy());
        Long primaryPlantTeam = firstTeamId(effPlantTeams);
        detailsMapper.update(null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PlantDetails>()
                .in(PlantDetails::getId, startableDetailIds)
                .set(PlantDetails::getBeginActualdate, bo.getBeginActualdate())
                .set(PlantDetails::getPlantBy, primaryPlantTeam)
                .set(PlantDetails::getPlantStatus, "ongoing")
                .set(PlantDetails::getUpdateBy, updateBy));
        // 班组多选中间表 sync（role=plant）：所选各明细同一套种植班组全集
        for (Long detailId : startableDetailIds) {
            teamLinkService.syncDetailTeams(detailId, PlantTeamLinkService.ROLE_PLANT, effPlantTeams);
        }

        // 采摘区间按实际开始日期重算（复用创建逻辑同一套 plusDays 公式，锚点从计划旬别换成 begin_actualdate）：
        // earliest_harvestdate = beginActualdate + crop.minCycle，last_harvestdate = beginActualdate + crop.maxCycle。
        // 各明细 cropId 可能不同 → 按 cropId 批量查 crop 后逐条 set；cycle 为空则跳过保留原值。
        Set<Long> startableSet = new java.util.HashSet<>(startableDetailIds);
        List<PlantDetails> startables = details.stream()
            .filter(d -> startableSet.contains(d.getId()))
            .toList();
        Set<Long> cropIds = startables.stream()
            .map(PlantDetails::getCropId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CropInfo> cropMap = cropIds.isEmpty()
            ? Map.of()
            : cropMapper.selectByIds(cropIds).stream()
                .collect(Collectors.toMap(CropInfo::getId, c -> c, (a, b) -> a));
        for (PlantDetails d : startables) {
            CropInfo crop = d.getCropId() == null ? null : cropMap.get(d.getCropId());
            if (crop == null || crop.getMinCycle() == null || crop.getMaxCycle() == null) {
                // cycle 未配置：跳过重算，保留创建时按计划旬别算出的原值
                continue;
            }
            LocalDate earliest = bo.getBeginActualdate().plusDays(crop.getMinCycle());
            LocalDate last = bo.getBeginActualdate().plusDays(crop.getMaxCycle());
            detailsMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PlantDetails>()
                    .eq(PlantDetails::getId, d.getId())
                    .set(PlantDetails::getEarliestHarvestdate, earliest)
                    .set(PlantDetails::getLastHarvestdate, last)
                    .set(PlantDetails::getUpdateBy, updateBy));
        }

        // 同步关联地块 plot_status=2（种植中）
        Set<Long> plotIds = details.stream()
            .filter(d -> startableDetailIds.contains(d.getId()))
            .map(PlantDetails::getPlotId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!plotIds.isEmpty()) {
            assertPlotsIdle(plotIds);   // row5：播种前校验地块为空地（上一轮须先退茬）
            plotMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PlotInfo>()
                    .in(PlotInfo::getId, plotIds)
                    .set(PlotInfo::getPlotStatus, 2)
                    .set(PlotInfo::getUpdateBy, updateBy));
        }

        // 派生重算受影响计划主表 plant_status（开工 done=0 时仍落 pending，无害；与 finishPlant 对称）；
        // 明细采摘窗口已按 begin_actualdate 重算 → 同步重算主表 earliest/last_harvestdate 聚合（详情头部取主表）
        Set<Long> affectedPlanIds = details.stream()
            .filter(d -> startableSet.contains(d.getId()))
            .map(PlantDetails::getPlantId).filter(Objects::nonNull).collect(Collectors.toSet());
        for (Long pid : affectedPlanIds) {
            baseMapper.recalcPlanStatus(pid);
            baseMapper.recalcAggregates(pid);
        }
        return startableDetailIds.size();
    }

    /**
     * row5（何涛 2026-07-17）：播种/首次种植前置校验——目标地块必须为空地（plot_status=1 空闲）。
     *
     * <p>上一轮种植过的地块须先退茬（submitRotation：采摘态 3 → 空闲 1）才能继续播种下一轮计划；
     * 非空地即拒绝并列出地块编号。字典 djs_plot_status：1=空闲 / 2=种植 / 3=采摘。</p>
     */
    private void assertPlotsIdle(Set<Long> plotIds) {
        if (CollUtil.isEmpty(plotIds)) {
            return;
        }
        List<PlotInfo> plots = plotMapper.selectList(
            new LambdaQueryWrapper<PlotInfo>().in(PlotInfo::getId, plotIds));
        List<String> notIdle = plots.stream()
            .filter(p -> p.getPlotStatus() == null || p.getPlotStatus() != 1)
            .map(p -> p.getPlotCode() != null ? p.getPlotCode() : ("id=" + p.getId()))
            .toList();
        if (!notIdle.isEmpty()) {
            throw new ServiceException("地块 " + String.join("、", notIdle)
                + " 非空地状态，须先退茬成空地才能播种（上一轮种植未退茬）");
        }
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
        // 一步落地：可完成 = 尚未完成（plant_status != 'completed' 且 end_actualdate IS NULL）。
        // 取消开工分步后，待种植(begin 空)直接置完成；历史「进行中」(begin 已有)也可完成。
        List<PlantDetails> finishables = details.stream()
            .filter(d -> !"completed".equals(d.getPlantStatus()) && d.getEndActualdate() == null)
            .toList();
        if (finishables.isEmpty()) {
            // 所选明细均已完成：幂等返 0（非错误，前端按 0 行提示"已完成"）
            return 0;
        }
        Long updateBy = currentUserIdSafe();
        LocalDate completeDate = bo.getEndActualdate();

        // 对尚未开工(begin 空)的明细：补 begin_actualdate=完成日期(单日期口径) + plant_by，
        // 并按 begin 重算采摘窗口 + 关联地块 plot_status=2（复用 startPlant 同一套逻辑）
        List<PlantDetails> needBackfill = finishables.stream()
            .filter(d -> d.getBeginActualdate() == null)
            .toList();
        if (!needBackfill.isEmpty()) {
            List<Long> backfillIds = needBackfill.stream().map(PlantDetails::getId).toList();
            List<Long> effPlantTeams = effectiveTeamIds(bo.getPlantByIds(), bo.getPlantBy());
            Long primaryPlantTeam = firstTeamId(effPlantTeams);
            detailsMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PlantDetails>()
                    .in(PlantDetails::getId, backfillIds)
                    .set(PlantDetails::getBeginActualdate, completeDate)
                    .set(PlantDetails::getPlantBy, primaryPlantTeam)
                    .set(PlantDetails::getUpdateBy, updateBy));
            // 班组多选中间表 sync（role=plant）
            for (Long detailId : backfillIds) {
                teamLinkService.syncDetailTeams(detailId, PlantTeamLinkService.ROLE_PLANT, effPlantTeams);
            }

            // 采摘区间按种植(完成)日期重算：earliest = date + crop.minCycle，last = date + crop.maxCycle。
            Set<Long> cropIds = needBackfill.stream()
                .map(PlantDetails::getCropId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, CropInfo> cropMap = cropIds.isEmpty()
                ? Map.of()
                : cropMapper.selectByIds(cropIds).stream()
                    .collect(Collectors.toMap(CropInfo::getId, c -> c, (a, b) -> a));
            for (PlantDetails d : needBackfill) {
                CropInfo crop = d.getCropId() == null ? null : cropMap.get(d.getCropId());
                if (crop == null || crop.getMinCycle() == null || crop.getMaxCycle() == null) {
                    // cycle 未配置：跳过重算，保留创建时按计划旬别算出的原值
                    continue;
                }
                LocalDate earliest = completeDate.plusDays(crop.getMinCycle());
                LocalDate last = completeDate.plusDays(crop.getMaxCycle());
                detailsMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PlantDetails>()
                        .eq(PlantDetails::getId, d.getId())
                        .set(PlantDetails::getEarliestHarvestdate, earliest)
                        .set(PlantDetails::getLastHarvestdate, last)
                        .set(PlantDetails::getUpdateBy, updateBy));
            }

            // 同步关联地块 plot_status=2（种植中）
            Set<Long> plotIds = needBackfill.stream()
                .map(PlantDetails::getPlotId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!plotIds.isEmpty()) {
                assertPlotsIdle(plotIds);   // row5：首次种植（未开工直接完成）前校验地块为空地
                plotMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PlotInfo>()
                        .in(PlotInfo::getId, plotIds)
                        .set(PlotInfo::getPlotStatus, 2)
                        .set(PlotInfo::getUpdateBy, updateBy));
            }
        }

        // 批量回写明细：plant_status='completed' + end_actualdate
        List<Long> finishableDetailIds = finishables.stream().map(PlantDetails::getId).toList();
        detailsMapper.update(null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PlantDetails>()
                .in(PlantDetails::getId, finishableDetailIds)
                .set(PlantDetails::getPlantStatus, "completed")
                .set(PlantDetails::getEndActualdate, completeDate)
                .set(PlantDetails::getUpdateBy, updateBy));

        // 派生重算受影响计划主表 plant_status（任一完成→ongoing / 全部完成→completed）；
        // 未开工明细补种后采摘窗口已重算 → 同步重算主表 earliest/last_harvestdate 聚合（详情头部取主表）
        Set<Long> affectedPlanIds = finishables.stream()
            .map(PlantDetails::getPlantId).filter(Objects::nonNull).collect(Collectors.toSet());
        for (Long pid : affectedPlanIds) {
            baseMapper.recalcPlanStatus(pid);
            baseMapper.recalcAggregates(pid);
        }
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
    public PlantPlanStatsVo getPlanStats(Integer planYear) {
        // 计划年份默认当年（前端筛选默认当年；不传 / 传 null 时兜底）
        int year = planYear != null ? planYear : LocalDate.now().getYear();
        PlantPlanStatsVo vo = new PlantPlanStatsVo();

        // ① 当前空地块数（plot_status=1，当前状态，不随年份变）+ 地块总数（频次分母）
        Map<String, Object> plotStats = baseMapper.selectPlotStatusStats();
        vo.setIdlePlot(plotStats == null ? 0 : toInt(plotStats.get("idlePlot")));
        int totalPlot = plotStats == null ? 0 : toInt(plotStats.get("totalPlot"));

        // ②③⑤ 当年计划/种植维度聚合（plan_year = year）
        Map<String, Object> planStats = baseMapper.selectPlanDetailStats(year);
        int plantedPlot = planStats == null ? 0 : toInt(planStats.get("plantedPlot"));   // ② 当年已种植地块数 = 当年种植行为次数之和 COUNT(*)
        int plannedPlot = planStats == null ? 0 : toInt(planStats.get("plannedPlot"));   // ③ 当年计划种植地块数 = Σ计划地块 COUNT(*)
        int cropVariety = planStats == null ? 0 : toInt(planStats.get("cropVarietyCount")); // ⑤ 当年计划种植作物品种数 DISTINCT crop_id
        vo.setPlantedPlot(plantedPlot);
        vo.setPlannedPlot(plannedPlot);
        vo.setCropVarietyCount(cropVariety);

        // ④ 当年计划地块使用频次 = 当年计划种植地块数 / 地块总数（2 位小数；地块总数 0 时 0）
        if (totalPlot > 0) {
            vo.setPlotUsageFreq(BigDecimal.valueOf(plannedPlot)
                .divide(BigDecimal.valueOf(totalPlot), 2, RoundingMode.HALF_UP));
        } else {
            vo.setPlotUsageFreq(BigDecimal.ZERO);
        }
        return vo;
    }
}
