package org.dromara.djs.plant.perf.service.impl;

import cn.idev.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.file.FileUtils;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.excel.utils.ExcelWriterWrapper;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.plant.farm.domain.query.FarmRecordsQuery;
import org.dromara.djs.plant.farm.domain.vo.FarmRecordsVo;
import org.dromara.djs.plant.farm.service.IFarmRecordsService;
import org.dromara.djs.plant.perf.domain.PlantWorkPerformance;
import org.dromara.djs.plant.perf.domain.query.PlantWorkPerformanceQuery;
import org.dromara.djs.plant.perf.domain.vo.FarmCountRow;
import org.dromara.djs.plant.perf.domain.vo.PerfActivityAggRow;
import org.dromara.djs.plant.perf.domain.vo.PerfAggRow;
import org.dromara.djs.plant.perf.domain.vo.PlotCropTeamRow;
import org.dromara.djs.plant.perf.domain.vo.PerfDetailCropExportVo;
import org.dromara.djs.plant.perf.domain.vo.PerfDetailFarmExportVo;
import org.dromara.djs.plant.perf.domain.vo.PerfListRow;
import org.dromara.djs.plant.perf.domain.vo.PlantWorkPerformanceVo;
import org.dromara.djs.plant.perf.mapper.PlantWorkPerformanceMapper;
import org.dromara.djs.plant.perf.service.IPlantWorkPerformanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 班组绩效结算 Service 实现（PLT-PERF-001）。
 *
 * <p>核心 {@code generate(statMonth)}：幂等软删该月旧行 → 聚合 details.actual_yield（公斤）→
 * 读 crop.pick_unit_price（元/公斤）作单价快照，金额 = 采摘量(公斤) × 绩效百分比 × 单价(元/公斤)
 * → 批量 INSERT。单价取快照（不实时 JOIN），后续改价不污染历史月。</p>
 *
 * <p>V6 row107：绩效百分比来自采摘录入（{@code handle_record.perf_percent}），且是分组维度 ——
 * 同一「班组 × 作物 × 产品」按不同百分比录入的采摘各落一行，详情里就能看到绩效是怎么组成的。</p>
 *
 * @author djs
 * @since PLT-PERF-001
 */
@Slf4j
@Service
public class PlantWorkPerformanceServiceImpl
    extends DjsBaseServiceImpl<PlantWorkPerformanceMapper, PlantWorkPerformance>
    implements IPlantWorkPerformanceService {

    /**
     * 统计月份格式 yyyy-MM。
     */
    private static final Pattern STAT_MONTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");

    /**
     * 软删值（区别于普通 del_flag='1'：'2'=被重新生成覆盖）。MP @TableLogic 仍按 '0' 过滤活动行。
     */
    private static final String DEL_FLAG_SUPERSEDED = "2";

    /**
     * 绩效百分比默认值（%）：采摘活动没有这个维度、存量流水也没填，一律按全额计绩效（V6 row107）。
     */
    private static final int DEFAULT_PERF_PERCENT = 100;

    /**
     * 农事记录 Service（详情导出 sheet2 数据源，只调用不改其内部实现）。
     */
    private final IFarmRecordsService farmRecordsService;

    public PlantWorkPerformanceServiceImpl(PlantWorkPerformanceMapper baseMapper,
                                           IFarmRecordsService farmRecordsService) {
        super(baseMapper);
        this.farmRecordsService = farmRecordsService;
    }

    @Override
    public TableDataInfo<PerfListRow> queryPageList(PlantWorkPerformanceQuery query, PageQuery pageQuery) {
        String statMonth = query != null ? query.getStatMonth() : null;
        Long teamId = query != null ? query.getTeamId() : null;
        IPage<PerfListRow> page = baseMapper.selectTeamMonthPage(pageQuery.build(), statMonth, teamId);
        enrichListRows(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<PerfListRow> queryList(PlantWorkPerformanceQuery query) {
        String statMonth = query != null ? query.getStatMonth() : null;
        Long teamId = query != null ? query.getTeamId() : null;
        // 导出走全量（不分页）：用一个大页拉回全部聚合行，与主列表口径一致
        Page<PerfListRow> page = new Page<>(1, Integer.MAX_VALUE, false);
        List<PerfListRow> list = baseMapper.selectTeamMonthPage(page, statMonth, teamId).getRecords();
        enrichListRows(list);
        return list;
    }

    @Override
    public List<PlantWorkPerformanceVo> queryCropRows(Long teamId, String statMonth) {
        if (teamId == null || StringUtils.isBlank(statMonth)) {
            return Collections.emptyList();
        }
        List<PlantWorkPerformanceVo> rows = baseMapper.selectCropRowsByTeamMonth(teamId, statMonth);
        enrichCropNames(rows);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generate(String statMonth) {
        if (StringUtils.isBlank(statMonth) || !STAT_MONTH_PATTERN.matcher(statMonth).matches()) {
            throw new ServiceException("统计月份格式必须为 yyyy-MM（如 2026-04）");
        }
        // 1. 幂等清理：软删该月已有结算行（del_flag '0' → '2'）
        Long updateBy = currentUserIdSafe();
        Date now = new Date();
        UpdateWrapper<PlantWorkPerformance> clearWrapper = Wrappers.<PlantWorkPerformance>update()
            .eq("stat_month", statMonth)
            .eq("del_flag", "0")
            .set("del_flag", DEL_FLAG_SUPERSEDED)
            .set("update_by", updateBy)
            .set("update_time", now);
        baseMapper.update(null, clearWrapper);

        // 2. 聚合该月 班组 × 作物 × 产品 采摘总量（毛菜处理过磅 + 采摘活动平摊，row12 / V6 row20）
        List<PerfAggRow> rawRows = baseMapper.aggregateByMonth(statMonth);
        if (rawRows.isEmpty() && baseMapper.selectActivityAggByMonth(statMonth).isEmpty()) {
            return 0;
        }
        // ⚠️ 产品归属必须在合并**之前**定下来：存量过磅流水 productId 有值、采摘活动没有产品维度，
        // 若等合并完再把 null 折算成「首个配置产品」，两者的合并键（…:123 与 …:null）永远撞不到一起，
        // 最后却双双落成同一个 productId → 同一「班组 × 作物 × 产品」被拆成两行（金额总额对、明细多行）。
        Set<Long> cropIds = Stream.concat(
                rawRows.stream().map(PerfAggRow::getCropId),
                baseMapper.selectActivityAggByMonth(statMonth).stream().map(PerfActivityAggRow::getCropId))
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));
        Map<Long, Long> primaryProductMap = loadPrimaryProducts(cropIds);
        rawRows.forEach(r -> {
            if (r.getProductId() == null) {
                r.setProductId(primaryProductMap.get(r.getCropId()));
            }
            if (r.getPerfPercent() == null) {
                r.setPerfPercent(DEFAULT_PERF_PERCENT);
            }
        });
        List<PerfAggRow> aggRows = mergeActivityShares(rawRows, statMonth, primaryProductMap);
        if (aggRows.isEmpty()) {
            return 0;
        }

        // 3. 批量取单价快照（一次查询）：V6 row20 起单价来自「作物 × 产品」的绩效金额，
        //    作物没配产品 / 该产品没填金额时回落作物级 pick_unit_price，历史月口径不变。
        Map<Long, BigDecimal> cropPriceMap = loadCropPrices(cropIds);
        Map<String, BigDecimal> productPriceMap = loadCropProductPrices(cropIds);

        // 4. 逐组算金额 + 批量 INSERT
        int inserted = 0;
        List<PlantWorkPerformance> rows = new ArrayList<>(aggRows.size());
        for (PerfAggRow agg : aggRows) {
            BigDecimal pickWeight = agg.getPickWeight() != null ? agg.getPickWeight() : BigDecimal.ZERO;
            // 产品归属已在合并前定死（含把 null 折算成首个配置产品），这里直接用
            Long productId = agg.getProductId();
            BigDecimal unitPrice = productPriceMap.get(agg.getCropId() + ":" + productId);
            if (unitPrice == null) {
                unitPrice = cropPriceMap.getOrDefault(agg.getCropId(), BigDecimal.ZERO);
            }
            // V6 row107：金额 = 采摘量(公斤) × 绩效百分比 × 单价快照(元/公斤)，保留 2 位（元）。
            // 单价录入即公斤价，两侧同单位直乘；百分比最后一步除 100，只在末尾做一次舍入。
            int perfPercent = agg.getPerfPercent() != null ? agg.getPerfPercent() : DEFAULT_PERF_PERCENT;
            BigDecimal amount = pickWeight.multiply(unitPrice)
                .multiply(BigDecimal.valueOf(perfPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            PlantWorkPerformance row = new PlantWorkPerformance();
            row.setStatMonth(statMonth);
            row.setTeamId(agg.getTeamId());
            row.setCropId(agg.getCropId());
            row.setProductId(productId);
            row.setPerfPercent(perfPercent);
            row.setPickWeight(pickWeight);
            row.setUnitPriceSnapshot(unitPrice);
            row.setPerformanceAmount(amount);
            row.setPerformanceRule(perfRule(unitPrice, perfPercent));
            // del_flag / tenant_id / 审计字段走 MP MetaObjectHandler + @TableLogic 默认，不手工赋 tenant_id
            rows.add(row);
        }
        for (PlantWorkPerformance row : rows) {
            inserted += baseMapper.insert(row);
        }
        return inserted;
    }

    @Override
    public void exportDetail(Long teamId, String statMonth, HttpServletResponse response) {
        if (teamId == null) {
            throw new ServiceException("班组 ID 不能为空");
        }
        if (StringUtils.isBlank(statMonth) || !STAT_MONTH_PATTERN.matcher(statMonth).matches()) {
            throw new ServiceException("统计月份格式必须为 yyyy-MM（如 2026-04）");
        }
        // sheet1 产量绩效：与详情抽屉产量绩效 tab 同口径（逐作物行）
        List<PerfDetailCropExportVo> cropRows = queryCropRows(teamId, statMonth).stream()
            .map(r -> {
                PerfDetailCropExportVo vo = new PerfDetailCropExportVo();
                vo.setCropName(r.getCropName());
                vo.setProductName(r.getProductName());
                vo.setPickWeight(r.getPickWeight());
                vo.setPerfPercent(r.getPerfPercent());
                vo.setUnitPriceSnapshot(r.getUnitPriceSnapshot());
                vo.setPerformanceAmount(r.getPerformanceAmount());
                return vo;
            })
            .collect(Collectors.toList());
        // sheet2 农事记录：与详情抽屉农事记录 tab 同口径（farm_by = teamId + farm_date ∈ 整月，全量不分页）
        YearMonth month = YearMonth.parse(statMonth);
        FarmRecordsQuery farmQuery = new FarmRecordsQuery();
        farmQuery.setFarmBy(teamId);
        farmQuery.setFarmDateBegin(month.atDay(1));
        farmQuery.setFarmDateEnd(month.atEndOfMonth());
        List<PerfDetailFarmExportVo> farmRows = new ArrayList<>();
        for (FarmRecordsVo record : farmRecordsService.queryList(farmQuery)) {
            PerfDetailFarmExportVo vo = new PerfDetailFarmExportVo();
            vo.setRecordNo(record.getRecordNo());
            vo.setFarmType(record.getFarmType());
            vo.setPlotName(record.getPlotName());
            vo.setFarmDate(record.getFarmDate());
            farmRows.add(vo);
        }
        // 双 sheet 写出：每个 sheet 各自指定表头类
        String filename = ExcelUtil.encodingFilename("绩效详情_" + statMonth);
        FileUtils.setAttachmentResponseHeader(response, filename);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
        try {
            ExcelUtil.exportExcel(PerfDetailCropExportVo.class, response.getOutputStream(), wrapper -> {
                WriteSheet cropSheet = ExcelWriterWrapper.sheetBuilder(0, "产量绩效")
                    .head(PerfDetailCropExportVo.class)
                    .build();
                wrapper.write(cropRows, cropSheet);
                WriteSheet farmSheet = ExcelWriterWrapper.sheetBuilder(1, "农事记录")
                    .head(PerfDetailFarmExportVo.class)
                    .build();
                // sheet2 表头类与 wrapper 泛型（sheet1 头类）不同，走底层 writer 写出
                wrapper.excelWriter().write(farmRows, farmSheet);
            });
        } catch (IOException e) {
            throw new ServiceException("绩效详情导出失败：" + e.getMessage());
        }
    }

    /**
     * 采摘活动量并入班组聚合（row12 + row43a）：按 (crop, plot) 聚合的活动量，经班组集合平摊
     * （多班组各计 1/N），合并进毛菜过磅聚合行。
     *
     * <p>班组集合按 (plotId:cropId) 解析，<b>COALESCE 优先</b>：先取活动<b>自身</b>中间表
     * {@code t_plant_activity_team} 直接指定的班组（{@link PlantWorkPerformanceMapper#selectActivityDirectTeamsByMonth}），
     * 该 (plot,crop) 无直接班组时才兜底地块采收班组（{@code role='harvest'}）。修复原逻辑「活动直接指定
     * 班组、但地块无 harvest 明细的整条活动量被跳过丢失」。两处都无班组归属才跳过（无法计入班组绩效）。</p>
     */
    private List<PerfAggRow> mergeActivityShares(List<PerfAggRow> aggRows, String statMonth,
                                                 Map<Long, Long> primaryProductMap) {
        List<PerfActivityAggRow> actRows = baseMapper.selectActivityAggByMonth(statMonth);
        if (actRows.isEmpty()) {
            return aggRows;
        }
        Set<Long> plotIds = actRows.stream()
            .map(PerfActivityAggRow::getPlotId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));
        // (plotId:cropId) -> 活动自身直接指定班组集合（首选）
        Map<String, Set<Long>> activityTeamsByPlotCrop = new HashMap<>();
        for (PlotCropTeamRow r : baseMapper.selectActivityDirectTeamsByMonth(statMonth)) {
            activityTeamsByPlotCrop.computeIfAbsent(r.getPlotId() + ":" + r.getCropId(), k -> new LinkedHashSet<>())
                .add(r.getTeamId());
        }
        // (plotId:cropId) -> 地块采收班组集合（兜底，DISTINCT，多计划共享班组只计一次）。
        // plotIds 可能为空——当月活动全是销售去向（pick_dest='sale'，plot_id 恒 NULL）时即如此；
        // selectHarvestTeamsByPlots 用 foreach 拼 IN，空集合会拼出 `IN ()` 让整个结算生成报错，
        // 故空集合直接跳过（这些活动的班组只能来自 activityTeamsByPlotCrop 直接指定集合）。
        Map<String, Set<Long>> harvestTeamsByPlotCrop = new HashMap<>();
        if (!plotIds.isEmpty()) {
            for (PlotCropTeamRow r : baseMapper.selectHarvestTeamsByPlots(plotIds)) {
                harvestTeamsByPlotCrop.computeIfAbsent(r.getPlotId() + ":" + r.getCropId(), k -> new LinkedHashSet<>())
                    .add(r.getTeamId());
            }
        }
        // 合并容器：既有过磅行按 (teamId:cropId:productId:perfPercent) 建索引
        Map<String, PerfAggRow> merged = new LinkedHashMap<>();
        for (PerfAggRow row : aggRows) {
            merged.put(mergeKey(row.getTeamId(), row.getCropId(), row.getProductId(), row.getPerfPercent()), row);
        }
        for (PerfActivityAggRow act : actRows) {
            String plotCropKey = act.getPlotId() + ":" + act.getCropId();
            // COALESCE 优先：活动自身直接指定班组 → 空时兜底地块采收班组
            Set<Long> teams = activityTeamsByPlotCrop.get(plotCropKey);
            if (teams == null || teams.isEmpty()) {
                teams = harvestTeamsByPlotCrop.get(plotCropKey);
            }
            if (teams == null || teams.isEmpty() || act.getPickWeight() == null) {
                continue;
            }
            BigDecimal share = act.getPickWeight()
                .divide(BigDecimal.valueOf(teams.size()), 3, RoundingMode.HALF_UP);
            // 采摘活动没有产品维度 → 归到该作物的首个配置产品，且**用同一个 productId 参与合并**，
            // 这样它就能和同产品的过磅行合成一行，而不是各自落一行。
            Long actProductId = primaryProductMap.get(act.getCropId());
            for (Long teamId : teams) {
                // 采摘活动没有绩效百分比维度 → 按 100% 归组，与同产品的 100% 过磅行合并成一行
                String key = mergeKey(teamId, act.getCropId(), actProductId, DEFAULT_PERF_PERCENT);
                PerfAggRow row = merged.computeIfAbsent(key, k -> {
                    PerfAggRow nr = new PerfAggRow();
                    nr.setTeamId(teamId);
                    nr.setCropId(act.getCropId());
                    nr.setProductId(actProductId);
                    nr.setPerfPercent(DEFAULT_PERF_PERCENT);
                    nr.setPickWeight(BigDecimal.ZERO);
                    return nr;
                });
                BigDecimal base = row.getPickWeight() != null ? row.getPickWeight() : BigDecimal.ZERO;
                row.setPickWeight(base.add(share));
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 聚合行合并键：班组 × 作物 × 产品 × 绩效百分比（V6 row107 起百分比进键，同产品不同比例各占一行）。
     */
    private static String mergeKey(Long teamId, Long cropId, Long productId, Integer perfPercent) {
        return teamId + ":" + cropId + ":" + productId + ":"
            + (perfPercent != null ? perfPercent : DEFAULT_PERF_PERCENT);
    }

    /**
     * 绩效规则描述：100% 时仍是纯单价（历史口径不变），非 100% 时把比例写进去，
     * 让结算行自己说明白金额是怎么算出来的。
     */
    private static String perfRule(BigDecimal unitPrice, int perfPercent) {
        String price = unitPrice.stripTrailingZeros().toPlainString() + " 元/公斤";
        return perfPercent == DEFAULT_PERF_PERCENT ? price : price + " × " + perfPercent + "%";
    }

    /**
     * 批量取作物单价快照映射。
     */
    private Map<Long, BigDecimal> loadCropPrices(Set<Long> cropIds) {
        Map<Long, BigDecimal> map = new HashMap<>();
        if (cropIds.isEmpty()) {
            return map;
        }
        List<Map<String, Object>> priceRows = baseMapper.selectCropUnitPrices(cropIds);
        for (Map<String, Object> r : priceRows) {
            Object idObj = r.get("cropId");
            Object priceObj = r.get("pickUnitPrice");
            if (idObj instanceof Number nid && priceObj instanceof BigDecimal price) {
                map.put(nid.longValue(), price);
            } else if (idObj instanceof Number nid && priceObj != null) {
                map.put(nid.longValue(), new BigDecimal(priceObj.toString()));
            }
        }
        return map;
    }

    /**
     * 批量取「作物 × 产品」绩效金额快照（V6 row20）。
     *
     * @return key = {@code cropId + ":" + productId}；value = 该产品的绩效金额（元/公斤）
     */
    private Map<String, BigDecimal> loadCropProductPrices(Set<Long> cropIds) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (cropIds.isEmpty()) {
            return map;
        }
        for (Map<String, Object> r : baseMapper.selectCropProductPrices(cropIds)) {
            Long cropId = toLong(r.get("cropId"));
            Long productId = toLong(r.get("productId"));
            BigDecimal price = toDecimal(r.get("perfPrice"));
            if (cropId != null && productId != null && price != null) {
                map.put(cropId + ":" + productId, price);
            }
        }
        return map;
    }

    /**
     * 每个作物的「首选产品」= 产品配置里的第一行（mapper 已按 {@code crop_id, id} 升序）。
     *
     * <p>用于给没有产品维度的聚合行（存量过磅流水 / 采摘活动）归一个产品，
     * 免得绩效详情里这些行的产品列一片空白。</p>
     */
    private Map<Long, Long> loadPrimaryProducts(Set<Long> cropIds) {
        Map<Long, Long> map = new HashMap<>();
        if (cropIds.isEmpty()) {
            return map;
        }
        for (Map<String, Object> r : baseMapper.selectCropProductPrices(cropIds)) {
            Long cropId = toLong(r.get("cropId"));
            Long productId = toLong(r.get("productId"));
            if (cropId != null && productId != null) {
                map.putIfAbsent(cropId, productId);
            }
        }
        return map;
    }

    private static Long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }

    private static BigDecimal toDecimal(Object v) {
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        return v == null ? null : new BigDecimal(v.toString());
    }

    /**
     * 批量给聚合列表行补 teamName + farmCount（避免 N+1，rework 134）。
     */
    private void enrichListRows(List<PerfListRow> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        // 1. teamName 批量反查 t_plant_work_team.team_name
        Set<Long> teamIds = list.stream()
            .map(PerfListRow::getTeamId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));
        Map<Long, String> teamNameMap = new HashMap<>();
        Map<Long, Integer> teamMemberCountMap = new HashMap<>();
        if (!teamIds.isEmpty()) {
            for (Map<String, Object> r : baseMapper.selectTeamNames(teamIds)) {
                if (r.get("teamId") instanceof Number n) {
                    teamNameMap.put(n.longValue(), (String) r.get("teamName"));
                }
            }
            // 班组人数：一次 IN 查 t_plant_work_people 活动成员数（rework 134/135）
            for (Map<String, Object> r : baseMapper.countTeamMembers(teamIds)) {
                if (r.get("teamId") instanceof Number n && r.get("cnt") instanceof Number c) {
                    teamMemberCountMap.put(n.longValue(), c.intValue());
                }
            }
        }
        // 2. farmCount 批量 count t_plant_farm_records（按 (teamId, statMonth) 组合一次取回）
        Map<String, Integer> farmCountMap = loadFarmCounts(list);
        for (PerfListRow row : list) {
            if (row.getTeamId() != null) {
                row.setTeamName(teamNameMap.get(row.getTeamId()));
                row.setTeamMemberCount(teamMemberCountMap.getOrDefault(row.getTeamId(), 0));
            }
            row.setFarmCount(farmCountMap.getOrDefault(farmKey(row.getTeamId(), row.getStatMonth()), 0));
        }
    }

    /**
     * 批量统计各 (班组, 月) 的农事次数，返回 key=teamId|statMonth / value=cnt。
     */
    private Map<String, Integer> loadFarmCounts(List<PerfListRow> list) {
        Map<String, Integer> map = new HashMap<>();
        List<PerfListRow> valid = list.stream()
            .filter(r -> r.getTeamId() != null && StringUtils.isNotBlank(r.getStatMonth()))
            .collect(Collectors.toList());
        if (valid.isEmpty()) {
            return map;
        }
        List<Long> teamIds = valid.stream().map(PerfListRow::getTeamId).distinct().collect(Collectors.toList());
        List<String> months = valid.stream().map(PerfListRow::getStatMonth).distinct().collect(Collectors.toList());
        for (FarmCountRow r : baseMapper.countFarmByTeamMonths(teamIds, months)) {
            if (r.getFarmBy() != null && StringUtils.isNotBlank(r.getStatMonth())) {
                map.put(farmKey(r.getFarmBy(), r.getStatMonth()), r.getCnt() != null ? r.getCnt() : 0);
            }
        }
        return map;
    }

    private String farmKey(Long teamId, String statMonth) {
        return teamId + "|" + statMonth;
    }

    /**
     * 批量给逐作物绩效行补 cropName（详情按作物分行，避免 N+1）。
     */
    private void enrichCropNames(List<PlantWorkPerformanceVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<Long> cropIds = list.stream()
            .map(PlantWorkPerformanceVo::getCropId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));
        Map<Long, String> cropNameMap = new HashMap<>();
        if (!cropIds.isEmpty()) {
            for (Map<String, Object> r : baseMapper.selectCropNames(cropIds)) {
                if (r.get("cropId") instanceof Number n) {
                    cropNameMap.put(n.longValue(), (String) r.get("cropName"));
                }
            }
        }
        // V6 row20：产品名一并批量补（详情「产品」列）
        Set<Long> productIds = list.stream()
            .map(PlantWorkPerformanceVo::getProductId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));
        Map<Long, String> productNameMap = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (Map<String, Object> r : baseMapper.selectProductNames(productIds)) {
                Long pid = toLong(r.get("productId"));
                if (pid != null) {
                    productNameMap.put(pid, (String) r.get("productName"));
                }
            }
        }
        for (PlantWorkPerformanceVo vo : list) {
            if (vo.getCropId() != null) {
                vo.setCropName(cropNameMap.get(vo.getCropId()));
            }
            if (vo.getProductId() != null) {
                vo.setProductName(productNameMap.get(vo.getProductId()));
            }
        }
    }
}
