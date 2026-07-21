package org.dromara.djs.warehouse.dashboard.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.dashboard.domain.vo.ChartSeriesItemVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.ChartTrendPointVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.LocationOverviewItemVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardChartsVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseDashboardSummaryVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehousePorkEfficiencyVo;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehouseVegEfficiencyVo;
import org.dromara.djs.warehouse.dashboard.mapper.WarehouseDashboardMapper;
import org.dromara.djs.warehouse.dashboard.mapper.WarehouseProductionDashboardMapper;
import org.dromara.djs.warehouse.dashboard.service.IWarehouseDashboardService;
import org.dromara.djs.warehouse.stat.domain.WarehouseCroppRecord;
import org.dromara.djs.warehouse.stat.domain.WarehouseIndicatorRecord;
import org.dromara.djs.warehouse.stat.domain.WarehouseMonthlyRecord;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 仓库看板聚合实现。
 *
 * <p>summary（3 KPI + 库位概览，mp 复用）+ charts（6 图 + 双横条 11 指标）。所有 null 用
 * 0 / 空列表兜底，折线按近 N 日补齐缺口日为 0。</p>
 *
 * @author djs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseDashboardServiceImpl implements IWarehouseDashboardService {

    private static final String DEFAULT_TENANT = "1001";

    /** 折线近几日（生产趋势 / 损耗趋势）。 */
    private static final int TREND_DAYS = 7;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 盘点结果 check_result_type → 中文标签（djs_check_result）。 */
    private static final Map<String, String> CHECK_RESULT_LABELS = new LinkedHashMap<>();

    static {
        CHECK_RESULT_LABELS.put("1", "正常");
        CHECK_RESULT_LABELS.put("2", "异常");
        CHECK_RESULT_LABELS.put("3", "计损");
    }

    /** 月份格式（yyyy-MM）。 */
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    /** 矩阵日期列格式（MM-dd）。 */
    private static final DateTimeFormatter MD_FMT = DateTimeFormatter.ofPattern("MM-dd");
    /** TOP10 取前几项。 */
    private static final int TOP_N = 10;
    /** kg → 吨。 */
    private static final BigDecimal KG_PER_TON = BigDecimal.valueOf(1000);

    private final WarehouseDashboardMapper dashboardMapper;
    private final WarehouseProductionDashboardMapper productionMapper;

    @Override
    public WarehouseDashboardSummaryVo getSummary() {
        String tenantId = currentTenant();

        WarehouseDashboardSummaryVo vo = new WarehouseDashboardSummaryVo();

        vo.setTodayDemandQuantity(nzd(dashboardMapper.sumTodayWhiteBarDemand(tenantId)));
        vo.setTodayProductionCount(nz(dashboardMapper.countTodayProduction(tenantId)));
        vo.setStockCheckNormal(nz(dashboardMapper.countLatestCheckNormal(tenantId)));
        vo.setStockCheckAbnormal(nz(dashboardMapper.countLatestCheckAbnormal(tenantId)));
        vo.setStockCheckLoss(nz(dashboardMapper.countLatestCheckLoss(tenantId)));
        vo.setMonthAbnormalLocationCount(nz(dashboardMapper.countMonthAbnormalLocation(tenantId)));

        List<LocationOverviewItemVo> overview = dashboardMapper.selectLocationOverview(tenantId);
        vo.setLocationOverview(overview == null ? List.of() : overview);

        return vo;
    }

    @Override
    public WarehouseDashboardChartsVo getCharts() {
        String tenantId = currentTenant();
        WarehouseDashboardChartsVo vo = new WarehouseDashboardChartsVo();

        // 图① 果蔬产品当日需求分布饼：近 30 日 vegetable 业态按产品名（对齐原型小白菜/大白菜/...）
        vo.setDemandByType(nullToEmpty(dashboardMapper.selectDemandByProductName(tenantId, "vegetable")));
        // 图①（对齐原型）明日产品需求分布（近 7 日）：猪肉全量 / 果蔬（前端切换 + TOP5 归其他）
        vo.setDemandPork(nullToEmpty(dashboardMapper.selectDemandByProductName7d(tenantId, "pork")));
        vo.setDemandVeg(nullToEmpty(dashboardMapper.selectDemandByProductName7d(tenantId, "vegetable")));

        // 图② 退货环：旧口径（方向）保留兼容；新口径按产品名分猪肉/果蔬，前端切换
        vo.setReturnByDirection(buildReturnByDirection(dashboardMapper.selectReturnByDirection(tenantId)));
        vo.setReturnPork(nullToEmpty(dashboardMapper.selectReturnByProductName(tenantId, "pork")));
        vo.setReturnVegetable(nullToEmpty(dashboardMapper.selectReturnByProductName(tenantId, "vegetable")));

        // 图③ 生产趋势：旧口径（总重）保留；新口径组合图（白条头数柱 + 猪肉/果蔬重量线），近 N 日补齐
        vo.setProductionTrend(fillTrend(dashboardMapper.selectProductionTrend(tenantId, TREND_DAYS)));
        vo.setProductionWhiteBarHeadTrend(fillTrend(dashboardMapper.selectWhiteBarHeadTrend(tenantId, TREND_DAYS)));
        vo.setProductionPorkWeightTrend(fillTrend(dashboardMapper.selectProductionWeightTrendByBelong(tenantId, TREND_DAYS, "pork")));
        vo.setProductionVegWeightTrend(fillTrend(dashboardMapper.selectProductionWeightTrendByBelong(tenantId, TREND_DAYS, "vegetable")));

        // 图④ 盘点结果饼：映射 1/2/3 中文标签
        vo.setCheckResult(buildCheckResult(dashboardMapper.selectCheckResult(tenantId)));

        // 图⑤ 异常库位：旧口径（异常 vs 正常）保留兼容；新口径当月异常库位按库位名分布环
        vo.setLocationHealth(buildLocationHealth(tenantId));
        vo.setAbnormalLocationByName(nullToEmpty(dashboardMapper.selectMonthAbnormalLocationByName(tenantId)));

        // 图⑥ 损耗趋势：旧口径（总量）保留；新口径多系列（猪肉/果蔬），近 N 日补齐
        vo.setLossTrend(fillTrend(dashboardMapper.selectLossTrend(tenantId, TREND_DAYS)));
        vo.setLossPorkTrend(fillTrend(dashboardMapper.selectLossTrendByBelong(tenantId, TREND_DAYS, "pork")));
        vo.setLossVegTrend(fillTrend(dashboardMapper.selectLossTrendByBelong(tenantId, TREND_DAYS, "vegetable")));

        // 横条 1「今日需求」8 项（对齐原型）+ 兼容旧 3 项
        vo.setTodayDemandWhiteBar(nzd(dashboardMapper.sumTodayWhiteBarHeads(tenantId)));
        vo.setTodayDemandPork(nzd(dashboardMapper.sumTodayDemandByBelong(tenantId, "pork")));
        vo.setTodayDemandOffal(BigDecimal.ZERO);   // 红白脏产品 V1 无对应 belong_type 数据源，默认 0
        vo.setTodayDemandGiftBox(nzd(dashboardMapper.sumTodayDemandByType(tenantId, "gift_box")));
        vo.setTodayDemandVegetableKinds(nz(dashboardMapper.countTodayDemandKindsByBelong(tenantId, "vegetable")));
        vo.setTodayDemandVegetable(nzd(dashboardMapper.sumTodayDemandByBelong(tenantId, "vegetable")));
        vo.setTodayDemandEgg(nzd(dashboardMapper.sumTodayDemandByBelong(tenantId, "egg")));
        vo.setTodayDemandDryGood(nzd(dashboardMapper.sumTodayDemandByBelong(tenantId, "dry_good")));
        vo.setTodayDemandOther(nzd(dashboardMapper.sumTodayDemandByType(tenantId, "other")));
        vo.setTodayDemandOrderCount(nz(dashboardMapper.countTodayDemandOrders(tenantId)));
        vo.setTodayDemandTotal(nzd(dashboardMapper.sumTodayDemandTotal(tenantId)));

        // 横条 2「今日生产」8 项（对齐原型）+ 兼容旧 5 项
        vo.setTodaySlaughterPigCount(nz(dashboardMapper.countTodaySlaughterPigs(tenantId)));
        vo.setTodayWhiteBarWeight(nzd(dashboardMapper.sumTodayWhiteBarWeight(tenantId)));
        vo.setTodayCutBarCount(nzd(dashboardMapper.countTodayCutBars(tenantId)));
        vo.setTodayCutProductWeight(nzd(dashboardMapper.sumTodayCutProductWeight(tenantId)));
        vo.setTodayVegReceiveKinds(nz(dashboardMapper.countTodayVegReceiveKinds(tenantId)));
        vo.setTodayVegReceiveWeight(nzd(dashboardMapper.sumTodayVegReceiveWeight(tenantId)));
        vo.setTodayVegProductKinds(nz(dashboardMapper.countTodayVegProductKinds(tenantId)));
        vo.setTodayVegProductWeight(nzd(dashboardMapper.sumTodayVegProductWeight(tenantId)));
        vo.setTodayProductionCount(nz(dashboardMapper.countTodayProduction(tenantId)));
        vo.setTodayProductionWeight(nzd(dashboardMapper.sumTodayProductionWeight(tenantId)));
        vo.setTodayInboundCount(nz(dashboardMapper.countTodayFlowByDirection(tenantId, "IN")));
        vo.setTodayOutboundCount(nz(dashboardMapper.countTodayFlowByDirection(tenantId, "OT")));
        vo.setTodayLossQuantity(nzd(dashboardMapper.sumTodayLoss(tenantId)));

        return vo;
    }

    // ============================================================
    //  mp 仓库管理看板 tab1：猪只分割效能管理（WMS-DASH-MP-001）
    // ============================================================

    @Override
    public WarehousePorkEfficiencyVo getPorkEfficiency(Integer year, String month) {
        String tenantId = currentTenant();
        int y = year == null ? LocalDate.now().getYear() : year;
        YearMonth ym = parseMonthOrNow(month);

        WarehousePorkEfficiencyVo vo = new WarehousePorkEfficiencyVo();
        vo.setYear(y);

        // ---- 年度屠宰 / 分割 8 KPI：当年日表 Σ ----
        List<WarehouseIndicatorRecord> yearRows = productionMapper.selectIndicatorRecordsInRange(
            tenantId, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));

        int slaughterCount = sumInt(yearRows, WarehouseIndicatorRecord::getSlaughterCount);
        BigDecimal slaughterWeight = sumDec(yearRows, WarehouseIndicatorRecord::getSlaughterWeight);
        BigDecimal arriveWeight = sumDec(yearRows, WarehouseIndicatorRecord::getArriveWeight);
        BigDecimal barTotalWeight = sumDec(yearRows, WarehouseIndicatorRecord::getBarTotalWeight);
        BigDecimal cutBarCount = sumDec(yearRows, WarehouseIndicatorRecord::getCutBarCount);
        BigDecimal cutBarWeight = sumDec(yearRows, WarehouseIndicatorRecord::getCutBarWeight);
        BigDecimal cutProductWeight = sumDec(yearRows, WarehouseIndicatorRecord::getCutProductWeight);

        vo.setSlaughterCount(slaughterCount);
        vo.setAvgSlaughterWeight(rate(slaughterWeight, BigDecimal.valueOf(slaughterCount), 2));
        vo.setSlaughterRate(pct(arriveWeight, slaughterWeight));
        vo.setBarYieldRate(pct(barTotalWeight, slaughterWeight));
        vo.setCutBarCount(cutBarCount);
        vo.setCutBarWeightTon(toTon(cutBarWeight));
        vo.setCutProductWeightTon(toTon(cutProductWeight));
        vo.setCutRate(pct(cutProductWeight, cutBarWeight));

        // ---- 月度屠宰效能趋势：当年月表（柱=屠宰头数 + 3 折线出品率）----
        List<WarehouseMonthlyRecord> monthlyRows = productionMapper.selectMonthlyRecordsByYear(tenantId, y + "-%");
        for (WarehouseMonthlyRecord m : monthlyRows) {
            vo.getTrendMonths().add(m.getStatMonth());
            vo.getTrendSlaughterCount().add(nz(m.getSlaughterCount()));
            vo.getTrendSlaughterRate().add(nzd(m.getSlaughterRate()));
            vo.getTrendBarYieldRate().add(nzd(m.getBarYieldRate()));
            vo.getTrendCutYieldRate().add(nzd(m.getCutYieldRate()));
        }

        // ---- 日猪肉处理数据矩阵：所选月份昨日→月初倒序的日表行 ----
        List<LocalDate> matrixDates = matrixDates(ym);
        List<String> dayLabels = new ArrayList<>();
        for (LocalDate d : matrixDates) {
            dayLabels.add(d.format(MD_FMT));
        }
        vo.setMatrixDays(dayLabels);

        // 该月日表行按 stat_date 索引
        LocalDate mFirst = ym.atDay(1);
        LocalDate mLast = ym.atEndOfMonth();
        Map<LocalDate, WarehouseIndicatorRecord> byDate = new LinkedHashMap<>();
        for (WarehouseIndicatorRecord r : productionMapper.selectIndicatorRecordsInRange(tenantId, mFirst, mLast)) {
            if (r.getStatDate() != null) {
                byDate.put(r.getStatDate(), r);
            }
        }
        vo.setMatrixRows(buildPorkMatrix(matrixDates, byDate));

        return vo;
    }

    /** 日猪肉处理矩阵指标定义（中文文案 + 取值器 + 是否率/均值类[累计留空]）。 */
    private record PorkMetric(String label,
                              Function<WarehouseIndicatorRecord, Object> getter,
                              boolean rateOrAvg) {
    }

    /** 日猪肉处理矩阵 12 指标行（对齐原型「日猪肉处理数据统计」）。 */
    private static final List<PorkMetric> PORK_METRICS = List.of(
        new PorkMetric("屠宰头数", WarehouseIndicatorRecord::getSlaughterCount, false),
        new PorkMetric("送宰均重", WarehouseIndicatorRecord::getAvgSlaughterWeight, true),
        new PorkMetric("接收均重", WarehouseIndicatorRecord::getArriveWeight, false),
        new PorkMetric("屠宰率", WarehouseIndicatorRecord::getSlaughterRate, true),
        new PorkMetric("白条均重", WarehouseIndicatorRecord::getAvgBarWeight, true),
        new PorkMetric("白条出品率", WarehouseIndicatorRecord::getBarYieldRate, true),
        new PorkMetric("分割白条数", WarehouseIndicatorRecord::getCutBarCount, false),
        new PorkMetric("预冷损耗", WarehouseIndicatorRecord::getPrecoolLoss, false),
        new PorkMetric("分割总重", WarehouseIndicatorRecord::getCutBarWeight, false),
        new PorkMetric("分割产品重", WarehouseIndicatorRecord::getCutProductWeight, false),
        new PorkMetric("分割率", WarehouseIndicatorRecord::getCutRate, true),
        // R80：文案「分割损耗重」→「分割损耗」。
        new PorkMetric("分割损耗", WarehouseIndicatorRecord::getCutLoss, false)
    );

    /**
     * 组装日猪肉处理矩阵行（逐日取值 + 累计；率/均值类累计留空）。
     *
     * @param dates  日期列（与 VO matrixDays 同序）
     * @param byDate 该月日表行按日索引
     * @return 12 指标行
     */
    private List<WarehousePorkEfficiencyVo.MatrixRow> buildPorkMatrix(
        List<LocalDate> dates, Map<LocalDate, WarehouseIndicatorRecord> byDate) {
        List<WarehousePorkEfficiencyVo.MatrixRow> rows = new ArrayList<>();
        for (PorkMetric m : PORK_METRICS) {
            WarehousePorkEfficiencyVo.MatrixRow row = new WarehousePorkEfficiencyVo.MatrixRow();
            row.setMetric(m.label());
            List<String> daily = new ArrayList<>();
            BigDecimal sum = BigDecimal.ZERO;
            boolean hasAny = false;
            // R80：率/均值类累计分母 = 有数据（值 >0）天数，无活动天（0/空）不摊薄均值。
            int dataDays = 0;
            for (LocalDate d : dates) {
                WarehouseIndicatorRecord r = byDate.get(d);
                Object raw = r == null ? null : m.getter().apply(r);
                // R81：率/均值类当日空值兜底显示 0.00（前端 withPct 再补 %），非率类保持原样（空/数值）。
                daily.add(m.rateOrAvg() && raw == null ? "0.00" : fmtCell(raw));
                if (raw instanceof Number n) {
                    BigDecimal bd = new BigDecimal(n.toString());
                    sum = sum.add(bd);
                    hasAny = true;
                    if (bd.signum() > 0) {
                        dataDays++;
                    }
                }
            }
            row.setDailyValues(daily);
            if (m.rateOrAvg()) {
                // R80：率/均值类累计 = 每日之和 / 有数据天数（原为留空）；无数据 → 0.00 兜底（对齐 R81 空值口径）。
                row.setTotal(dataDays > 0
                    ? fmtCell(sum.divide(new BigDecimal(dataDays), 2, RoundingMode.HALF_UP))
                    : "0.00");
            } else {
                // 整数 / 重量类累计 = 逐日之和；全月无数据留空。
                row.setTotal(hasAny ? fmtCell(sum) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    // ============================================================
    //  mp 仓库管理看板 tab2：果蔬处理效能管理（WMS-DASH-MP-001）
    // ============================================================

    @Override
    public WarehouseVegEfficiencyVo getVegEfficiency(Integer year, String month) {
        String tenantId = currentTenant();
        int y = year == null ? LocalDate.now().getYear() : year;
        YearMonth ym = parseMonthOrNow(month);

        WarehouseVegEfficiencyVo vo = new WarehouseVegEfficiencyVo();
        vo.setYear(y);

        Map<String, String> cropNames = cropNameMap(tenantId);

        // ---- 年度蔬菜 6 KPI：品类数 + TOP/率从作物日表；率/总重口径从日表汇总 ----
        List<WarehouseCroppRecord> yearCropRows = productionMapper.selectCroppRecordsInRange(
            tenantId, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        List<WarehouseIndicatorRecord> yearDayRows = productionMapper.selectIndicatorRecordsInRange(
            tenantId, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));

        // 收获品类 = 当年作物日表去重作物数
        long kinds = yearCropRows.stream()
            .map(WarehouseCroppRecord::getCropId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .count();
        vo.setHarvestCropKinds((int) kinds);
        // 收获蔬菜总重(吨) = 当年作物采摘量之和；鲜菜总重(吨) = 当年发往月台量之和
        vo.setHarvestWeightTon(toTon(sumDec(yearCropRows, WarehouseCroppRecord::getPickWeight)));
        vo.setFreshWeightTon(toTon(sumDec(yearCropRows, WarehouseCroppRecord::getSendPlatformWeight)));
        // 蔬菜处理率 = (毛菜称量−毛菜损耗)/毛菜称量；路损率 = (发往−接收)/发往；净菜损耗率 = (生产+录入损耗)/(领用−退回)
        BigDecimal vegWeigh = sumDec(yearDayRows, WarehouseIndicatorRecord::getVegWeighWeight);
        BigDecimal vegLoss = sumDec(yearDayRows, WarehouseIndicatorRecord::getVegLoss);
        BigDecimal sendPlatform = sumDec(yearDayRows, WarehouseIndicatorRecord::getSendPlatformWeight);
        BigDecimal receivePlatform = sumDec(yearDayRows, WarehouseIndicatorRecord::getReceivePlatformWeight);
        BigDecimal prodLoss = sumDec(yearDayRows, WarehouseIndicatorRecord::getProdLossWeight);
        BigDecimal prodPick = sumDec(yearDayRows, WarehouseIndicatorRecord::getProdPickWeight);
        vo.setVegHandleRate(pct(vegWeigh.subtract(vegLoss), vegWeigh));
        vo.setTransportLossRate(pct(sendPlatform.subtract(receivePlatform), sendPlatform));
        vo.setNetVegLossRate(pct(prodLoss, prodPick));

        // ---- TOP10：所选月份作物日表，按作物聚合 ----
        LocalDate mFirst = ym.atDay(1);
        LocalDate mLast = ym.atEndOfMonth();
        List<WarehouseCroppRecord> monthCropRows = productionMapper.selectCroppRecordsInRange(tenantId, mFirst, mLast);

        // 收获量 TOP10：Σ pick_weight 按作物
        Map<Long, BigDecimal> harvestByCrop = new LinkedHashMap<>();
        // 损耗率 TOP10：综合损耗率 = 毛菜处理率反推损耗 + 路损率 + 净菜损耗率，逐作物取月内均值
        Map<Long, BigDecimal> lossRateSum = new LinkedHashMap<>();
        Map<Long, Integer> lossRateCnt = new LinkedHashMap<>();
        for (WarehouseCroppRecord r : monthCropRows) {
            Long cid = r.getCropId();
            if (cid == null) {
                continue;
            }
            harvestByCrop.merge(cid, nzd(r.getPickWeight()), BigDecimal::add);
            // 综合损耗率：毛菜间损耗率(=100−毛菜处理率) + 路损率 + 净菜间损失率
            BigDecimal vegHandleLoss = r.getVegHandleRate() == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(100).subtract(r.getVegHandleRate());
            BigDecimal combined = nzd(vegHandleLoss).add(nzd(r.getTransportLossRate())).add(nzd(r.getNetVegLossRate()));
            lossRateSum.merge(cid, combined, BigDecimal::add);
            lossRateCnt.merge(cid, 1, Integer::sum);
        }
        vo.setHarvestTop10(topN(harvestByCrop, cropNames, TOP_N));
        Map<Long, BigDecimal> lossRateAvg = new LinkedHashMap<>();
        for (Map.Entry<Long, BigDecimal> e : lossRateSum.entrySet()) {
            int cnt = lossRateCnt.getOrDefault(e.getKey(), 1);
            lossRateAvg.put(e.getKey(), rate(e.getValue(), BigDecimal.valueOf(cnt), 2));
        }
        vo.setLossRateTop10(topN(lossRateAvg, cropNames, TOP_N));

        // ---- 日蔬菜处理矩阵：所选月份最近一日（昨日 / 月末）的作物行 ----
        LocalDate matrixDay = latestMatrixDay(ym);
        if (matrixDay != null) {
            List<WarehouseCroppRecord> dayRows = productionMapper.selectCroppRecordsInRange(tenantId, matrixDay, matrixDay);
            for (WarehouseCroppRecord r : dayRows) {
                WarehouseVegEfficiencyVo.VegDailyRow row = new WarehouseVegEfficiencyVo.VegDailyRow();
                row.setCropName(cropNames.getOrDefault(String.valueOf(r.getCropId()), "未知作物"));
                row.setPickWeight(fmtCell(r.getPickWeight()));
                row.setFreshWeight(fmtCell(r.getSendPlatformWeight()));
                row.setFeedWeight(fmtCell(r.getFeedWeight()));
                row.setVegHandleRate(fmtCell(r.getVegHandleRate()));
                vo.getDailyRows().add(row);
            }
        }

        return vo;
    }

    /**
     * Map(cropId → 值) 取 TOP N 降序，映射作物名。
     *
     * @param byCrop    cropId → 聚合值
     * @param cropNames cropId(字符串) → 作物名
     * @param n         取前几项
     * @return TOP N name+value 横条项（降序）
     */
    private List<WarehouseVegEfficiencyVo.NameValue> topN(Map<Long, BigDecimal> byCrop,
        Map<String, String> cropNames, int n) {
        return byCrop.entrySet().stream()
            .sorted(Comparator.comparing((Map.Entry<Long, BigDecimal> e) -> nzd(e.getValue())).reversed())
            .limit(n)
            .map(e -> {
                WarehouseVegEfficiencyVo.NameValue nv = new WarehouseVegEfficiencyVo.NameValue();
                nv.setName(cropNames.getOrDefault(String.valueOf(e.getKey()), "未知作物"));
                nv.setValue(nzd(e.getValue()));
                return nv;
            })
            .toList();
    }

    /**
     * 作物名映射（cropId 字符串 → cropName）。
     *
     * @param tenantId 租户
     * @return cropId(字符串) → 作物名
     */
    private Map<String, String> cropNameMap(String tenantId) {
        Map<String, String> map = new LinkedHashMap<>();
        for (WarehouseProductionDashboardMapper.CropNameRow row : productionMapper.selectCropNames(tenantId)) {
            if (row.getIdStr() != null) {
                map.put(row.getIdStr(), row.getCropName());
            }
        }
        return map;
    }

    // ============================================================
    //  生产效能看板共享 helper
    // ============================================================

    /**
     * 解析月份 yyyy-MM；空 / 非法 → 当月（不打断查询）。
     *
     * @param month yyyy-MM 字符串（可空）
     * @return YearMonth
     */
    private YearMonth parseMonthOrNow(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month, MONTH_FMT);
        } catch (Exception e) {
            log.warn("[WarehouseDashboard] 月份入参非法 {}，回退当月", month);
            return YearMonth.now();
        }
    }

    /**
     * 矩阵日期列：从 min(昨日, 月末) 倒排到月初（昨日 → 月初降序，只到已发生日）。
     *
     * <p>对齐养殖活动统计：今天 1 号查本月时昨日落上月 → 区间空 → 返回空列表。</p>
     *
     * @param ym 目标月份
     * @return 日期列（降序，昨日在前）
     */
    private List<LocalDate> matrixDates(YearMonth ym) {
        LocalDate first = ym.atDay(1);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate last = ym.atEndOfMonth().isAfter(yesterday) ? yesterday : ym.atEndOfMonth();
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = last; !d.isBefore(first); d = d.minusDays(1)) {
            dates.add(d);
        }
        return dates;
    }

    /**
     * 矩阵最近一日（= min(昨日, 月末)）；查本月而昨日落上月时返 null。
     *
     * @param ym 目标月份
     * @return 最近一个已发生统计日，无则 null
     */
    private LocalDate latestMatrixDay(YearMonth ym) {
        List<LocalDate> dates = matrixDates(ym);
        return dates.isEmpty() ? null : dates.get(0);
    }

    /**
     * 整数列求和（null 当 0）。
     *
     * @param rows   行集
     * @param getter 取值器
     * @param <T>    行类型
     * @return 求和
     */
    private <T> int sumInt(List<T> rows, Function<T, Integer> getter) {
        int sum = 0;
        for (T r : rows) {
            sum += nz(getter.apply(r));
        }
        return sum;
    }

    /**
     * BigDecimal 列求和（null 当 0）。
     *
     * @param rows   行集
     * @param getter 取值器
     * @param <T>    行类型
     * @return 求和
     */
    private <T> BigDecimal sumDec(List<T> rows, Function<T, BigDecimal> getter) {
        BigDecimal sum = BigDecimal.ZERO;
        for (T r : rows) {
            sum = sum.add(nzd(getter.apply(r)));
        }
        return sum;
    }

    /**
     * 占比% = 分子/分母×100，2 位小数；分母≤0 → null（无意义不造假，前端显 —）。
     *
     * @param numerator   分子
     * @param denominator 分母
     * @return 百分比或 null
     */
    private BigDecimal pct(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() <= 0) {
            return null;
        }
        return numerator.multiply(BigDecimal.valueOf(100))
            .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    /**
     * 比值 = 分子/分母，指定小数位；分母≤0 → null。
     *
     * @param numerator   分子
     * @param denominator 分母
     * @param scale       小数位
     * @return 比值或 null
     */
    private BigDecimal rate(BigDecimal numerator, BigDecimal denominator, int scale) {
        if (denominator == null || denominator.signum() <= 0) {
            return null;
        }
        return numerator.divide(denominator, scale, RoundingMode.HALF_UP);
    }

    /**
     * kg → 吨（÷1000），3 位小数。
     *
     * @param kg 千克值
     * @return 吨
     */
    private BigDecimal toTon(BigDecimal kg) {
        return nzd(kg).divide(KG_PER_TON, 3, RoundingMode.HALF_UP);
    }

    /**
     * 矩阵单元格格式化：整数原样、BigDecimal 2 位小数、null 空串。
     *
     * @param raw 原始值
     * @return 格式化字符串
     */
    private String fmtCell(Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof BigDecimal bd) {
            return bd.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        return raw.toString();
    }

    /**
     * 图② 退货环：映射退货方向为中文标签。
     *
     * @param rows mapper 原始行（name=return_direction 值）
     * @return series（中文标签 + 计数）
     */
    private List<ChartSeriesItemVo> buildReturnByDirection(List<ChartSeriesItemVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ChartSeriesItemVo> result = new ArrayList<>();
        for (ChartSeriesItemVo row : rows) {
            String label = switch (nullToEmpty(row.getName())) {
                case "store_to_warehouse" -> "门店退仓";
                case "warehouse_to_supplier" -> "仓退供应商";
                default -> "其他";
            };
            result.add(new ChartSeriesItemVo(label, nzd(row.getValue())));
        }
        return result;
    }

    /**
     * 图④ 盘点饼：映射 check_result_type（1/2/3）为中文标签。
     *
     * @param rows mapper 原始行（name=check_result_type 数值字符串）
     * @return series（中文标签 + 计数）
     */
    private List<ChartSeriesItemVo> buildCheckResult(List<ChartSeriesItemVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ChartSeriesItemVo> result = new ArrayList<>();
        for (ChartSeriesItemVo row : rows) {
            String label = CHECK_RESULT_LABELS.getOrDefault(nullToEmpty(row.getName()), "其他");
            result.add(new ChartSeriesItemVo(label, nzd(row.getValue())));
        }
        return result;
    }

    /**
     * 图⑤ 异常库位环：当月异常 vs 正常库位数（两值固定返回，便于前端环形对比）。
     *
     * @param tenantId 租户
     * @return [异常库位, 正常库位] 两项 series
     */
    private List<ChartSeriesItemVo> buildLocationHealth(String tenantId) {
        BigDecimal abnormal = BigDecimal.valueOf(nz(dashboardMapper.countMonthAbnormalLocation(tenantId)));
        BigDecimal normal = BigDecimal.valueOf(nz(dashboardMapper.countMonthNormalLocation(tenantId)));
        List<ChartSeriesItemVo> result = new ArrayList<>();
        result.add(new ChartSeriesItemVo("异常库位", abnormal));
        result.add(new ChartSeriesItemVo("正常库位", normal));
        return result;
    }

    /**
     * 折线近 N 日补齐：mapper 只返回有数据的日期，这里补齐 [今天-N+1, 今天] 每天一个点，缺口补 0。
     *
     * @param rows mapper 原始日聚合行
     * @return 连续 N 天的 date+value 点
     */
    private List<ChartTrendPointVo> fillTrend(List<ChartTrendPointVo> rows) {
        Map<String, BigDecimal> raw = new LinkedHashMap<>();
        if (rows != null) {
            for (ChartTrendPointVo p : rows) {
                if (p.getDate() != null) {
                    raw.put(p.getDate(), nzd(p.getValue()));
                }
            }
        }
        List<ChartTrendPointVo> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            String day = today.minusDays(i).format(DAY_FMT);
            result.add(new ChartTrendPointVo(day, raw.getOrDefault(day, BigDecimal.ZERO)));
        }
        return result;
    }

    /**
     * null → 空列表（series 直传前的 null-safe 兜底）。
     *
     * @param rows mapper 原始 series 行（可空）
     * @param <T>  series 元素类型
     * @return 非空列表
     */
    private <T> List<T> nullToEmpty(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    /**
     * null → 0（整数）。
     *
     * @param v 可空整数
     * @return 非空整数
     */
    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * null → 0（BigDecimal）。
     *
     * @param v 可空 BigDecimal
     * @return 非空 BigDecimal
     */
    private BigDecimal nzd(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * null → 空串。
     *
     * @param s 可空字符串
     * @return 非空字符串
     */
    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * 取当前租户；V1 单租户场景或异常时回退 {@value #DEFAULT_TENANT}。
     *
     * @return 当前租户 ID
     */
    private String currentTenant() {
        try {
            String t = TenantHelper.getTenantId();
            return t == null || t.isEmpty() ? DEFAULT_TENANT : t;
        } catch (Exception e) {
            log.warn("[WarehouseDashboard] 获取租户失败，回退默认租户", e);
            return DEFAULT_TENANT;
        }
    }

}
