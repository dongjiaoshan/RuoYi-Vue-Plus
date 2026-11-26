package org.dromara.djs.warehouse.stat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.dashboard.mapper.WarehouseProductionDashboardMapper;
import org.dromara.djs.warehouse.loss.service.IProductionLossService;
import org.dromara.djs.warehouse.stat.domain.WarehouseCroppRecord;
import org.dromara.djs.warehouse.stat.domain.WarehouseIndicatorRecord;
import org.dromara.djs.warehouse.stat.domain.WarehouseMonthlyRecord;
import org.dromara.djs.warehouse.stat.domain.vo.WarehouseAnnualRecordVo;
import org.dromara.djs.warehouse.stat.domain.vo.WarehouseCroppRecordVo;
import org.dromara.djs.warehouse.stat.domain.vo.WarehouseIndicatorRecordVo;
import org.dromara.djs.warehouse.stat.domain.vo.WarehouseMonthlyRecordVo;
import org.dromara.djs.warehouse.stat.mapper.WarehouseCroppRecordMapper;
import org.dromara.djs.warehouse.stat.mapper.WarehouseIndicatorRecordMapper;
import org.dromara.djs.warehouse.stat.mapper.WarehouseMonthlyRecordMapper;
import org.dromara.djs.warehouse.stat.mapper.WarehouseStatAggregateMapper;
import org.dromara.djs.warehouse.stat.mapper.WarehouseStatAggregateMapper.VegStockBalance;
import org.dromara.djs.warehouse.stat.service.IWarehouseStatService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 仓库统计指标聚合 + 只读查询实现（WMS-STAT-001，邓博 admin row16/17/18）。
 *
 * <p>聚合顺序：日表 + 作物日表 UPSERT → 月表汇总当月日表。月率从已落盘日表 Σ 回读，
 * 故日表必须先写。所有源表按自然日 {@code DATE(col)=statDate} 聚合，BigDecimal 统一 setScale(3)。</p>
 *
 * <h3>已拍口径</h3>
 * <ol>
 *   <li>猪肉段全部按<b>接收 cohort</b>取数（D-0140，甲方 V6 行284）：一头猪只属于它在燎毛间的接收日
 *     {@code DATE(COALESCE(bar.arrive_time, bar.in_time))}，后续哪天再处理都算回这一天。屠宰头数 / 送宰均重 /
 *     接收均重 / 白条均重以接收头数为分母；白条出品率在接收批缺源出栏重时为空。
 *     定时任务每天整行重算 T-1，再回补最近 {@value #INDICATOR_REFRESH_DAYS} 天的接收批及 V1.1 生产指标。
 *     处理完成 cohort（bar.finish_time）→ 处理完成头数 / 该批猪接收重量之和，两个诊断列，不参与任何比率或均值。
 *     日表落率的重量基数；白条月出品率使用正值日均值</li>
 *   <li>路损率 = (发往月台−月台接收)/发往月台×100（A2 日表，分母用发往=损耗占发出量）；作物表按 row17 (发往−接收)/接收×100</li>
 *   <li>所有损耗一律从 loss_flow 按 loss_type 取（防重复计）</li>
 *   <li>月台接收只算自产 receive_type=1</li>
 *   <li>作物日表维度 = crop_id</li>
 *   <li>分割率 = 分割产品总重/分割白条总重（B1：分母 = 分割白条总重 cut_record.pickup_weight，Kevin 确认）</li>
 *   <li>作物净菜损耗（row17）生产/录入损耗按 product_id→crop(主产品) 归集（B5：plot 桥接漏 product 维损耗）</li>
 *   <li>作物饲喂量（row17）取权威台账 feed_log（毛菜间 + 仓库领用两路径），不取 handle_record handle_target=3</li>
 * </ol>
 *
 * @author djs
 * @since WMS-STAT-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseStatServiceImpl implements IWarehouseStatService {

    public static final String BUSINESS_ZONE = "Asia/Shanghai";
    private static final ZoneId SHANGHAI = ZoneId.of(BUSINESS_ZONE);
    private static final String DEFAULT_TENANT = "1001";
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 历史日指标每日回补窗口（天），不含单独全量重算的 T-1。 */
    public static final int INDICATOR_REFRESH_DAYS = 31;

    private final WarehouseStatAggregateMapper aggregateMapper;
    private final WarehouseIndicatorRecordMapper indicatorMapper;
    private final WarehouseCroppRecordMapper croppMapper;
    private final WarehouseMonthlyRecordMapper monthlyMapper;
    private final IProductionLossService productionLossService;
    private final WarehouseProductionDashboardMapper productionMapper;

    // ============================================================
    //  Write end — aggregate
    // ============================================================

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public String aggregate(LocalDate targetDate) {
        if (targetDate == null) {
            targetDate = businessToday().minusDays(1); // T-1
        }
        String tenantId = currentTenant();
        String statDate = targetDate.toString();
        String month = YearMonth.from(targetDate).format(MONTH_FMT);
        lockMonth(tenantId, month);
        log.info("[WarehouseStat] start tenant={} date={}", tenantId, statDate);

        // 果蔬生产损耗（per-product 残差）并入本任务：先写 loss_flow production_loss，再算 cropp
        // （cropp 净菜损耗率读 production_loss，同一次跑先写后读 → 即时新鲜，不再依赖独立 job 时序）。
        // 原独立 ProductionLossAggregateJob（0:50）已撤，改由本任务驱动。
        productionLossService.aggregate(targetDate);

        upsertDaily(tenantId, statDate);
        int croppRows = upsertCropp(tenantId, statDate);
        upsertMonthly(tenantId, month);

        log.info("[WarehouseStat] done tenant={} date={} croppRows={}", tenantId, statDate, croppRows);
        return String.format("ok | tenant=%s | date=%s | month=%s | tables=[indicator_record, cropp_record(%d), monthly_record]",
            tenantId, statDate, month, croppRows);
    }

    /**
     * 落盘仓库日表 t_warehouse_indicator_record（row16，UPSERT 幂等）。
     */
    private void upsertDaily(String tenantId, String statDate) {
        WarehouseIndicatorRecord r = new WarehouseIndicatorRecord();
        r.setStatDate(LocalDate.parse(statDate));
        r.setTenantId(tenantId);
        fillProductionIndicators(tenantId, statDate, r);
        r.setPrecoolLoss(scale3(aggregateMapper.sumLossByType(tenantId, statDate, "precool_loss")));

        // 毛菜段
        BigDecimal vegWeigh = scale3(aggregateMapper.sumVegWeighWeight(tenantId, statDate));
        BigDecimal vegLoss = scale3(aggregateMapper.sumLossByType(tenantId, statDate, "veg_handle_loss"));
        r.setVegWeighWeight(vegWeigh);
        r.setVegLoss(vegLoss);
        r.setVegLossRate(pctOrNull(vegLoss, vegWeigh));

        // 果蔬月台段
        BigDecimal sendPlatform = scale3(aggregateMapper.sumSendPlatformWeight(tenantId, statDate));
        BigDecimal receivePlatform = scale3(aggregateMapper.sumReceivePlatformWeight(tenantId, statDate));
        r.setSendPlatformWeight(sendPlatform);
        r.setReceivePlatformWeight(receivePlatform);
        // 口径#2（A2）日表 row16：路损率 = (发往月台−月台接收)/发往月台×100，分母统一用「发往」（损耗占发出量语义）。
        // spec row16 文字写「接收/发往」漏了减法（那是接收占比不是损耗率）；按损耗率口径取 (发往−接收)/发往。
        // 注：作物表 row17 分母用「接收」（见 upsertCropp），与本日表 row16 各自跟对应 spec 行，刻意不一致。
        r.setTransportLossRate(pctOrNull(sendPlatform.subtract(receivePlatform), sendPlatform));

        upsertIndicatorRow(tenantId, statDate, r);
    }

    /** T-1 全量和滚动回刷共用的日报计算。 */
    private void fillProductionIndicators(String tenantId, String statDate, WarehouseIndicatorRecord r) {
        fillPorkSegment(tenantId, statDate, r);

        // 处理完成 cohort（bar.finish_time）：两个诊断列，不参与任何比率/均值。
        // 记的是「这一天有几头猪走完燎毛间」，与上面按接收日归集的猪肉段是两件事
        // （同一头猪可以 A 日接收、B 日才点处理完成）。
        Map<String, Object> finished = aggregateMapper.selectFinishedAgg(tenantId, statDate);
        r.setFinishedCount(mapInt(finished, "finishedCount"));
        r.setFinishedArriveWeight(scale3(mapBd(finished, "finishedArriveWeight")));

        // 分割段
        BigDecimal cutProduct = scale3(aggregateMapper.sumCutProductWeight(tenantId, statDate));
        BigDecimal cutBar = scale3(aggregateMapper.sumCutBarWeight(tenantId, statDate));
        r.setCutBarCount(aggregateMapper.countCutBar(tenantId, statDate));
        r.setCutProductWeight(cutProduct);
        r.setCutOutputUnmatchedCount(aggregateMapper.countUnmatchedCutOutputs(tenantId, statDate));
        r.setCutBarWeight(cutBar);
        // 分子、分母均归同批白条的领用日。
        r.setCutRate(percent2OrZero(cutProduct, cutBar));
        r.setCutCompletionRate(percent2OrZero(
            BigDecimal.valueOf(aggregateMapper.countCompletedCutSides(tenantId, statDate)), r.getCutBarCount()));
        BigDecimal cutLoss = scale3(aggregateMapper.sumCutLossByPickup(tenantId, statDate));
        r.setCutLoss(cutLoss);
        r.setCutLossRate(percent2OrNull(cutLoss, cutBar));
        r.setCutPendingHalfCount(aggregateMapper.countPendingCutSides(tenantId, statDate));
        r.setSlaughterPendingCount(aggregateMapper.countPendingSlaughter(tenantId, statDate));

        Map<String, Object> ytd = aggregateMapper.selectSlaughterYtdCompletion(tenantId, statDate);
        int received = mapInt(ytd, "receivedCount");
        int completed = mapInt(ytd, "completedCount");
        r.setSlaughterYtdReceivedCount(received);
        r.setSlaughterYtdCompletedCount(completed);
        r.setSlaughterCompletionRate(received == 0 ? BigDecimal.ZERO.setScale(2)
            : percent2OrNull(BigDecimal.valueOf(completed), BigDecimal.valueOf(received)));

        r.setVegRawInboundWeight(scale3(aggregateMapper.sumVegRawInboundWeight(tenantId, statDate)));
        r.setVegDeliveryOutWeight(scale3(aggregateMapper.sumVegDeliveryOutWeight(tenantId, statDate)));
        r.setVegProducedPortionCount(aggregateMapper.countVegProducedPortions(tenantId, statDate));
        fillVegPackAndDemandIndicators(tenantId, statDate, r);
        LocalDate day = LocalDate.parse(statDate);
        var stock = aggregateMapper.selectLatestVegStockTotals(tenantId, day, day);
        r.setVegStockWeight(stock == null ? null : scale3(stock.stockWeight()));
        r.setVegStockUnconvertedCount(stock == null ? null : scale3(stock.unconvertedCount()));

        // 净菜生产段（果蔬口径，row206 邓博最终口径）——仓库聚合日表只统计「果蔬生产损耗」。
        // 果蔬生产损耗 = max(0, 果蔬领用 − 退回 − 录入损耗 − 饲喂 − 果蔬打包生产使用量)，全部 belong_type='vegetable'。
        //   · belong_type='vegetable' = 自产果蔬（外购商品 belong_type 空、包材非 vegetable，天然排除 → 符合「只算产品的」）。
        //   · 饲喂取 stock_flow feed_out（从生产领用池出库、天然 ≤ 领用），非 feed_log 的毛菜间/仓库饲喂。
        //   · 打包生产使用量取 product_production.material_consume（缺省回退 product_weight，均 kg），仅自产果蔬产品。
        //   · 「总生产损耗（全部产品）」按客户口径记在损耗总览表，不在本聚合日表展示；猪肉/其他产品损耗当前无展示需求。
        Map<String, Object> vegFlow = aggregateMapper.selectVegProdFlow(tenantId, statDate);
        BigDecimal vegPick = scale3(mapBd(vegFlow, "pickOut"));
        BigDecimal vegReturn = scale3(mapBd(vegFlow, "returnIn"));
        BigDecimal vegFeedOut = scale3(mapBd(vegFlow, "feedOut"));
        BigDecimal vegManualLoss = scale3(aggregateMapper.sumVegLossByType(tenantId, statDate, "manual_loss"));
        BigDecimal vegPackUsage = scale3(aggregateMapper.sumVegProdPackUsage(tenantId, statDate));
        BigDecimal prodLoss = scale3(
            vegPick.subtract(vegReturn).subtract(vegManualLoss).subtract(vegFeedOut).subtract(vegPackUsage)
                .max(BigDecimal.ZERO));
        r.setProdPickWeight(vegPick);
        r.setVegProdReturnWeight(vegReturn);
        r.setVegPackUsageWeight(vegPackUsage);
        r.setProdLossWeight(prodLoss);
        // 净菜损耗率 = (生产损耗 + 录入损耗)/(生产领用 − 生产退回)×100（果蔬口径，分母≤0 → null）
        BigDecimal netDenom = vegPick.subtract(vegReturn);
        r.setNetVegLossRate(pctOrNull(prodLoss.add(vegManualLoss), netDenom));
    }

    private void fillVegPackAndDemandIndicators(String tenantId, String statDate, WarehouseIndicatorRecord r) {
        var packs = aggregateMapper.selectVegPackIndicators(tenantId, statDate);
        r.setVegStandardPortionCount(mapInt(packs, "standardCount"));
        r.setVegOverspecPortionCount(mapInt(packs, "overspecCount"));
        r.setVegOverspecPercentSum(mapBd(packs, "overspecSum"));
        r.setVegOverspecRate(percent2OrZero(mapBd(packs, "overspecCount"), mapBd(packs, "standardCount")));
        r.setVegAvgOverspecPercent(divideOrZero(r.getVegOverspecPercentSum(), mapBd(packs, "overspecCount"), 2));
        r.setVegSpecQualifiedCount(mapInt(packs, "qualifiedCount"));
        r.setVegUndamagedQualifiedCount(mapInt(packs, "undamagedQualifiedCount"));
        r.setVegDamagedPortionCount(mapInt(packs, "damagedCount"));

        var demand = aggregateMapper.selectVegDemandFulfillment(tenantId, statDate);
        r.setVegDemandPortionCount(mapBd(demand, "requested"));
        r.setVegDemandFulfillmentRate(percent2OrZero(mapBd(demand, "fulfilled"), r.getVegDemandPortionCount()));
        r.setVegDemandKgExcludedCount(mapInt(demand, "kgExcluded"));
    }

    /** UPSERT 单条日表行（按 stat_date 查存在 → updateById / insert）。 */
    private void upsertIndicatorRow(String tenantId, String statDate, WarehouseIndicatorRecord r) {
        WarehouseIndicatorRecord existing = indicatorMapper.selectOne(
            new LambdaQueryWrapper<WarehouseIndicatorRecord>()
                .eq(WarehouseIndicatorRecord::getTenantId, tenantId)
                .eq(WarehouseIndicatorRecord::getStatDate, LocalDate.parse(statDate)));
        if (existing == null) {
            r.setDelFlag("0");
            r.setDelUnique(0L);
            indicatorMapper.insert(r);
        } else {
            r.setId(existing.getId());
            indicatorMapper.updateById(r);
        }
    }

    /**
     * 猪肉段当日指标：全部取<b>当日接收</b>的那批猪（接收 cohort，D-0140，甲方 V6 行284）。
     *
     * <p>一头猪只属于它在燎毛间的接收日，之后哪天再处理第二个半扇、哪天点处理完成，数据都算回接收那天。
     * 当天接收的猪还没处理完时这几列偏小，由 {@link #refreshProductionIndicators} 每天回补最近一个月补齐。</p>
     * <ul>
     *   <li>屠宰头数 = 当日接收的猪只头数</li>
     *   <li>送宰均重 = 这批猪的出栏重量之和 ÷ 接收头数</li>
     *   <li>接收均重 = 这批猪的接收重量之和 ÷ 接收头数</li>
     *   <li>白条均重 = 这批猪的白条产品重量之和 ÷ 接收头数</li>
     *   <li>屠宰出品率 = 接收重量之和 ÷ 出栏重量之和 × 100；白条出品率 = 白条产品重量之和 ÷ 出栏重量之和 × 100
     *       —— 使用完整接收批次的重量；白条出品率在批次缺源出栏重时没有可比值</li>
     * </ul>
     */
    private void fillPorkSegment(String tenantId, String statDate, WarehouseIndicatorRecord r) {
        Map<String, Object> c = aggregateMapper.selectReceivedCohortAgg(tenantId, statDate);
        int received = mapInt(c, "receivedCount");
        BigDecimal heads = BigDecimal.valueOf(received);
        BigDecimal baseWeight = scale3(mapBd(c, "baseWeight"));
        BigDecimal arrive = scale3(mapBd(c, "arriveWeight"));
        BigDecimal barWeight = scale3(mapBd(c, "barWeight"));
        BigDecimal rateArrive = arrive;
        BigDecimal rateBase = baseWeight;
        BigDecimal barYieldNumer = barWeight;

        r.setSlaughterCount(received);
        r.setSlaughterWeight(baseWeight);
        r.setAvgSlaughterWeight(divideOrZero(baseWeight, heads, 3));

        r.setArriveWeight(arrive);
        r.setArrivePigCount(received);
        r.setAvgArriveWeight(divideOrNull(arrive, heads));
        r.setSlaughterRateArriveWeight(rateArrive);
        r.setSlaughterRateBaseWeight(rateBase);
        r.setSlaughterRate(pctOrNull(rateArrive, rateBase));

        r.setBarTotalWeight(barWeight);
        r.setBarPigCount(received);
        r.setAvgBarWeight(divideOrZero(barWeight, heads, 3));
        r.setBarYieldNumerWeight(barYieldNumer);
        r.setBarYieldBaseWeight(rateBase);
        r.setBarYieldRate(mapInt(c, "missingBaseCount") > 0 ? null : percent2OrZero(barYieldNumer, rateBase));
    }

    /**
     * {@inheritDoc}
     *
     * <p>回补接收批、完成、分割和 V1.1 果蔬指标。缺失日行时补完整仓库日报，最后刷新涉及的月表。</p>
     */
    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public String refreshProductionIndicators(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("回补区间不合法：from=" + from + " to=" + to);
        }
        String tenantId = currentTenant();
        List<String> months = new ArrayList<>();
        YearMonth lastMonth = YearMonth.from(to);
        for (YearMonth month = YearMonth.from(from); !month.isAfter(lastMonth); month = month.plusMonths(1)) {
            String key = month.format(MONTH_FMT);
            lockMonth(tenantId, key);
            months.add(key);
        }
        int updated = 0;
        int created = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String statDate = d.toString();
            WarehouseIndicatorRecord existing = indicatorMapper.selectOne(
                new LambdaQueryWrapper<WarehouseIndicatorRecord>()
                    .eq(WarehouseIndicatorRecord::getTenantId, tenantId)
                    .eq(WarehouseIndicatorRecord::getStatDate, d));
            if (existing == null) {
                upsertDaily(tenantId, statDate);
                created++;
            } else {
                // 显式 SET 已计算列，避免 updateById 的 ALWAYS 清空未参与回刷的列。
                WarehouseIndicatorRecord p = new WarehouseIndicatorRecord();
                fillProductionIndicators(tenantId, statDate, p);
                indicatorMapper.update(null, new LambdaUpdateWrapper<WarehouseIndicatorRecord>()
                    .eq(WarehouseIndicatorRecord::getId, existing.getId())
                    .eq(WarehouseIndicatorRecord::getTenantId, tenantId)
                    .set(WarehouseIndicatorRecord::getSlaughterCount, p.getSlaughterCount())
                    .set(WarehouseIndicatorRecord::getSlaughterWeight, p.getSlaughterWeight())
                    .set(WarehouseIndicatorRecord::getAvgSlaughterWeight, p.getAvgSlaughterWeight())
                    .set(WarehouseIndicatorRecord::getArriveWeight, p.getArriveWeight())
                    .set(WarehouseIndicatorRecord::getArrivePigCount, p.getArrivePigCount())
                    .set(WarehouseIndicatorRecord::getAvgArriveWeight, p.getAvgArriveWeight())
                    .set(WarehouseIndicatorRecord::getSlaughterRateArriveWeight, p.getSlaughterRateArriveWeight())
                    .set(WarehouseIndicatorRecord::getSlaughterRateBaseWeight, p.getSlaughterRateBaseWeight())
                    .set(WarehouseIndicatorRecord::getSlaughterRate, p.getSlaughterRate())
                    .set(WarehouseIndicatorRecord::getBarTotalWeight, p.getBarTotalWeight())
                    .set(WarehouseIndicatorRecord::getBarPigCount, p.getBarPigCount())
                    .set(WarehouseIndicatorRecord::getAvgBarWeight, p.getAvgBarWeight())
                    .set(WarehouseIndicatorRecord::getBarYieldNumerWeight, p.getBarYieldNumerWeight())
                    .set(WarehouseIndicatorRecord::getBarYieldBaseWeight, p.getBarYieldBaseWeight())
                    .set(WarehouseIndicatorRecord::getBarYieldRate, p.getBarYieldRate())
                    .set(WarehouseIndicatorRecord::getFinishedCount, p.getFinishedCount())
                    .set(WarehouseIndicatorRecord::getFinishedArriveWeight, p.getFinishedArriveWeight())
                    .set(WarehouseIndicatorRecord::getSlaughterYtdReceivedCount, p.getSlaughterYtdReceivedCount())
                    .set(WarehouseIndicatorRecord::getSlaughterYtdCompletedCount, p.getSlaughterYtdCompletedCount())
                    .set(WarehouseIndicatorRecord::getSlaughterCompletionRate, p.getSlaughterCompletionRate())
                    .set(WarehouseIndicatorRecord::getCutBarCount, p.getCutBarCount())
                    .set(WarehouseIndicatorRecord::getCutBarWeight, p.getCutBarWeight())
                    .set(WarehouseIndicatorRecord::getCutProductWeight, p.getCutProductWeight())
                    .set(WarehouseIndicatorRecord::getCutOutputUnmatchedCount, p.getCutOutputUnmatchedCount())
                    .set(WarehouseIndicatorRecord::getCutRate, p.getCutRate())
                    .set(WarehouseIndicatorRecord::getCutLoss, p.getCutLoss())
                    .set(WarehouseIndicatorRecord::getCutLossRate, p.getCutLossRate())
                    .set(WarehouseIndicatorRecord::getCutPendingHalfCount, p.getCutPendingHalfCount())
                    .set(WarehouseIndicatorRecord::getSlaughterPendingCount, p.getSlaughterPendingCount())
                    .set(WarehouseIndicatorRecord::getCutCompletionRate, p.getCutCompletionRate())
                    .set(WarehouseIndicatorRecord::getVegRawInboundWeight, p.getVegRawInboundWeight())
                    .set(WarehouseIndicatorRecord::getVegDeliveryOutWeight, p.getVegDeliveryOutWeight())
                    .set(WarehouseIndicatorRecord::getVegProducedPortionCount, p.getVegProducedPortionCount())
                    .set(WarehouseIndicatorRecord::getVegStandardPortionCount, p.getVegStandardPortionCount())
                    .set(WarehouseIndicatorRecord::getVegOverspecPortionCount, p.getVegOverspecPortionCount())
                    .set(WarehouseIndicatorRecord::getVegOverspecPercentSum, p.getVegOverspecPercentSum())
                    .set(WarehouseIndicatorRecord::getVegOverspecRate, p.getVegOverspecRate())
                    .set(WarehouseIndicatorRecord::getVegAvgOverspecPercent, p.getVegAvgOverspecPercent())
                    .set(WarehouseIndicatorRecord::getVegSpecQualifiedCount, p.getVegSpecQualifiedCount())
                    .set(WarehouseIndicatorRecord::getVegUndamagedQualifiedCount, p.getVegUndamagedQualifiedCount())
                    .set(WarehouseIndicatorRecord::getVegDamagedPortionCount, p.getVegDamagedPortionCount())
                    .set(WarehouseIndicatorRecord::getVegDemandPortionCount, p.getVegDemandPortionCount())
                    .set(WarehouseIndicatorRecord::getVegDemandFulfillmentRate, p.getVegDemandFulfillmentRate())
                    .set(WarehouseIndicatorRecord::getVegDemandKgExcludedCount, p.getVegDemandKgExcludedCount())
                    .set(WarehouseIndicatorRecord::getVegStockWeight, p.getVegStockWeight())
                    .set(WarehouseIndicatorRecord::getVegStockUnconvertedCount, p.getVegStockUnconvertedCount())
                    .set(WarehouseIndicatorRecord::getVegProdReturnWeight, p.getVegProdReturnWeight())
                    .set(WarehouseIndicatorRecord::getVegPackUsageWeight, p.getVegPackUsageWeight())
                    .set(WarehouseIndicatorRecord::getProdPickWeight, p.getProdPickWeight())
                    .set(WarehouseIndicatorRecord::getProdLossWeight, p.getProdLossWeight())
                    .set(WarehouseIndicatorRecord::getNetVegLossRate, p.getNetVegLossRate()));
                updated++;
            }
        }
        for (String month : months) {
            upsertMonthly(tenantId, month);
        }
        log.info("[WarehouseStat] production indicator refresh tenant={} {}~{} updated={} created={} months={}",
            tenantId, from, to, updated, created, months);
        return String.format("ok | tenant=%s | indicators %s~%s | updated=%d | created=%d | months=%s",
            tenantId, from, to, updated, created, months);
    }

    @Override
    public List<LocalDate> findEarlierCutPickupDates(LocalDate from, LocalDate until) {
        String tenantId = currentTenant();
        var dates = new TreeSet<>(aggregateMapper.selectEarlierCutOutputPickupDates(tenantId, from, until));
        dates.addAll(aggregateMapper.selectEarlierChangedCutPickupDates(tenantId, from, until));
        return List.copyOf(dates);
    }

    @Override
    public List<LocalDate> findEarlierVegIndicatorDates(LocalDate from, LocalDate until) {
        String tenantId = currentTenant();
        var dates = new TreeSet<>(aggregateMapper.selectEarlierVegDamageDates(tenantId, from, until));
        dates.addAll(aggregateMapper.selectEarlierVegDemandDates(tenantId, from, until));
        return List.copyOf(dates);
    }

    public static LocalDate businessToday() {
        return LocalDate.now(SHANGHAI);
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public void captureVegStockSnapshot(LocalDate targetDate) {
        captureVegStockSnapshot(targetDate, false);
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public void captureMissingVegStockSnapshot(LocalDate targetDate) {
        captureVegStockSnapshot(targetDate, true);
    }

    private void captureVegStockSnapshot(LocalDate targetDate, boolean onlyIfMissing) {
        LocalDate today = businessToday();
        if (targetDate != null && targetDate.isBefore(today)) {
            throw new ServiceException("过去日期不补拍库存快照，已跳过");
        }
        if (!today.equals(targetDate)) {
            throw new ServiceException("库存快照只允许采集上海业务当天");
        }
        String tenantId = currentTenant();
        String month = YearMonth.from(today).format(MONTH_FMT);
        lockMonth(tenantId, month);
        verifySnapshotDate(today);
        if (onlyIfMissing && aggregateMapper.selectLatestVegStockTotals(tenantId, today, today) != null) {
            return;
        }
        var balances = aggregateMapper.selectCurrentVegStock(tenantId, aggregateMapper.selectKgVegMaterialIds(tenantId));
        verifySnapshotDate(today);

        aggregateMapper.deleteVegStockSnapshot(tenantId, today);
        // 库位 0 标记成功采集，即使本次不存在任何非零库存。
        aggregateMapper.insertVegStockSnapshot(IdWorker.getId(), tenantId, today,
            new VegStockBalance(0L, "", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        for (var balance : balances) {
            aggregateMapper.insertVegStockSnapshot(IdWorker.getId(), tenantId, today, balance);
        }

        upsertDaily(tenantId, today.toString());
        upsertMonthly(tenantId, month);
        verifySnapshotDate(today);
    }

    private static void verifySnapshotDate(LocalDate date) {
        if (!date.equals(businessToday())) {
            throw new ServiceException("已跨业务日，取消收工库存快照");
        }
    }

    /**
     * 先锁月表的唯一键，再读写日表。同月定时和手工回算串行，月合计读到已提交的前一次结果。
     * 月行尚未建立时，由 InnoDB 间隙锁保护插入；冲突的事务可在回滚后重跑。
     */
    private void lockMonth(String tenantId, String month) {
        monthlyMapper.selectOne(new LambdaQueryWrapper<WarehouseMonthlyRecord>()
            .eq(WarehouseMonthlyRecord::getTenantId, tenantId)
            .eq(WarehouseMonthlyRecord::getStatMonth, month)
            .eq(WarehouseMonthlyRecord::getDelUnique, 0L)
            .last("FOR UPDATE"));
    }

    /**
     * 落盘作物日表 t_warehouse_cropp_record（row17，按 crop_id 一行，UPSERT 幂等）。
     *
     * <p>当日有任意处理记录的作物才落盘。先取 active crop 并集，再各维度 GROUP 聚合组装到 Map，
     * 最后逐作物 UPSERT。</p>
     *
     * @return 落盘的作物行数
     */
    private int upsertCropp(String tenantId, String statDate) {
        List<Long> cropIds = aggregateMapper.selectActiveCropIds(tenantId, statDate);
        if (cropIds.isEmpty()) {
            return 0;
        }
        Map<Long, Map<String, Object>> handleAgg = indexByCropId(aggregateMapper.selectCropHandleAgg(tenantId, statDate));
        Map<Long, Map<String, Object>> feedAgg = indexByCropId(aggregateMapper.selectCropFeedAgg(tenantId, statDate));
        Map<Long, Map<String, Object>> receiveAgg = indexByCropId(aggregateMapper.selectCropReceiveAgg(tenantId, statDate));
        Map<Long, Map<String, Object>> flowAgg = indexByCropId(aggregateMapper.selectCropFlowAgg(tenantId, statDate));
        Map<Long, Map<String, Object>> lossAgg = indexByCropId(aggregateMapper.selectCropLossAgg(tenantId, statDate));

        int rows = 0;
        for (Long cropId : cropIds) {
            Map<String, Object> h = handleAgg.get(cropId);
            Map<String, Object> fd = feedAgg.get(cropId);
            Map<String, Object> rc = receiveAgg.get(cropId);
            Map<String, Object> f = flowAgg.get(cropId);
            Map<String, Object> l = lossAgg.get(cropId);

            BigDecimal pick = scale3(mapBd(h, "pickWeight"));
            // 饲喂量取权威台账 feed_log（毛菜间 + 仓库领用两路径），不再取 handle_record handle_target=3
            BigDecimal feed = scale3(mapBd(fd, "feedWeight"));
            BigDecimal sendPlatform = scale3(mapBd(h, "sendWeight"));
            BigDecimal receive = scale3(mapBd(rc, "receiveWeight"));
            BigDecimal prodPick = scale3(mapBd(f, "pickWeight"));
            BigDecimal prodReturn = scale3(mapBd(f, "returnWeight"));
            BigDecimal vegHandleLoss = scale3(mapBd(l, "vegHandleLoss"));
            BigDecimal productionLoss = scale3(mapBd(l, "productionLoss"));
            BigDecimal manualLoss = scale3(mapBd(l, "manualLoss"));

            WarehouseCroppRecord r = new WarehouseCroppRecord();
            r.setStatDate(LocalDate.parse(statDate));
            r.setCropId(cropId);
            r.setPickWeight(pick);
            r.setFeedWeight(feed);
            // 毛菜处理率 = (采摘−毛菜损耗)/采摘×100
            r.setVegHandleRate(pctOrNull(pick.subtract(vegHandleLoss), pick));
            r.setReceiveWeight(receive);
            r.setSendPlatformWeight(sendPlatform);
            // 路损率 row17：(发往−接收)/接收×100
            r.setTransportLossRate(pctOrNull(sendPlatform.subtract(receive), receive));
            // 出库量 = 生产领用−生产退回
            BigDecimal outWeight = scale3(prodPick.subtract(prodReturn));
            r.setOutWeight(outWeight);
            // 净菜损耗率 row17：(生产损耗+录入损耗)/出库量×100
            r.setNetVegLossRate(pctOrNull(productionLoss.add(manualLoss), outWeight));

            upsertCroppRow(tenantId, statDate, cropId, r);
            rows++;
        }
        return rows;
    }

    /** UPSERT 单条作物日表行（按 stat_date + crop_id 查存在 → updateById / insert）。 */
    private void upsertCroppRow(String tenantId, String statDate, Long cropId, WarehouseCroppRecord r) {
        WarehouseCroppRecord existing = croppMapper.selectOne(
            new LambdaQueryWrapper<WarehouseCroppRecord>()
                .eq(WarehouseCroppRecord::getStatDate, LocalDate.parse(statDate))
                .eq(WarehouseCroppRecord::getCropId, cropId));
        if (existing == null) {
            r.setDelFlag("0");
            r.setDelUnique(0L);
            croppMapper.insert(r);
        } else {
            r.setId(existing.getId());
            croppMapper.updateById(r);
        }
    }

    /**
     * 汇总仓库月表 t_warehouse_monthly_record（row18，从已落盘日表 Σ 回读，UPSERT 幂等）。
     * 白条出品率取正值日平均。分割率用同批产出总重除以领用总重。
     *
     * <p>本方法总是把当月<b>全部</b>日表行重扫一遍，所以逐日重算时每跑完一天刷一次月表都是安全的
     * （不是增量累加，最后一天跑完即得当月正确值）。</p>
     */
    private void upsertMonthly(String tenantId, String month) {
        Map<String, Object> m = aggregateMapper.sumMonthlyFromDaily(tenantId, month);

        WarehouseMonthlyRecord r = new WarehouseMonthlyRecord();
        r.setStatMonth(month);
        r.setTenantId(tenantId);
        r.setSlaughterCount(mapInt(m, "slaughterCount"));
        // 屠宰出品率 = Σ日分子 / Σ日分母 ×100
        r.setSlaughterRate(pctOrNull(mapBd(m, "sumRateArrive"), mapBd(m, "sumRateBase")));
        LocalDate firstDay = YearMonth.parse(month).atDay(1);
        var source = productionMapper.selectSlaughterInRange(tenantId, firstDay, firstDay.plusMonths(1));
        r.setSourceMarketingCount(source.getSlaughterCount());
        r.setSourceMarketingWeight(scale3(source.getSlaughterWeight()));
        r.setSourceMarketingAvgWeight(divideOrZero(r.getSourceMarketingWeight(),
            BigDecimal.valueOf(r.getSourceMarketingCount()), 3));

        r.setReceivedBarWeight(scale3(mapBd(m, "sumBarWeight")));
        r.setReceivedBarPigCount(mapInt(m, "sumBarPigCount"));
        r.setReceivedBarAvgWeight(divideOrZero(r.getReceivedBarWeight(), BigDecimal.valueOf(r.getReceivedBarPigCount()), 3));
        r.setSumDailyBarYieldRate(scale3(mapBd(m, "sumDailyBarRate")));
        r.setPositiveBarYieldDays(mapInt(m, "positiveBarRateDays"));
        // 与小程序月矩阵一致：使用日率合计和正值日数。
        r.setBarYieldRate(divideOrZero(r.getSumDailyBarYieldRate(), BigDecimal.valueOf(r.getPositiveBarYieldDays()), 2));
        r.setMatrixBarYieldRate(r.getBarYieldRate());
        r.setBarYieldRateSum(mapBd(m, "barYieldRateSum").setScale(2, RoundingMode.HALF_UP));
        r.setBarYieldRateDays(mapInt(m, "barYieldRateDays"));

        r.setCutBarCount(mapBd(m, "cutBarCount"));
        r.setCutBarWeight(scale3(mapBd(m, "sumCutBar")));
        r.setCutProductWeight(scale3(mapBd(m, "sumCutProduct")));
        r.setCutYieldRate(percent2OrZero(r.getCutProductWeight(), r.getCutBarWeight()));
        r.setCutLoss(scale3(mapBd(m, "cutLoss")));
        r.setCutLossPickupWeight(scale3(mapBd(m, "cutLossPickupWeight")));
        r.setCutLossRate(percent2OrNull(r.getCutLoss(), r.getCutLossPickupWeight()));
        r.setVegProductionPickWeight(scale3(mapBd(m, "vegProductionPickWeight")));
        r.setVegRawInboundWeight(scale3(mapBd(m, "vegRawInboundWeight")));
        r.setVegDeliveryOutWeight(scale3(mapBd(m, "vegDeliveryOutWeight")));
        r.setVegProducedPortionCount(mapInt(m, "vegProducedPortionCount"));
        fillVegMonthlyIndicators(tenantId, firstDay, m, r);

        WarehouseMonthlyRecord existing = monthlyMapper.selectOne(
            new LambdaQueryWrapper<WarehouseMonthlyRecord>()
                .eq(WarehouseMonthlyRecord::getTenantId, tenantId)
                .eq(WarehouseMonthlyRecord::getStatMonth, month));
        if (existing == null) {
            r.setDelFlag("0");
            r.setDelUnique(0L);
            monthlyMapper.insert(r);
        } else {
            r.setId(existing.getId());
            monthlyMapper.updateById(r);
        }
    }

    private void fillVegMonthlyIndicators(String tenantId, LocalDate firstDay, Map<String, Object> m,
                                           WarehouseMonthlyRecord r) {
        r.setVegDemandFulfillmentRate(mapBd(m, "vegDemandRate").setScale(2, RoundingMode.HALF_UP));
        r.setVegDemandKgExcludedCount(mapInt(m, "vegDemandKgExcludedCount"));
        BigDecimal netPickup = r.getVegProductionPickWeight().subtract(mapBd(m, "vegReturnWeight"));
        r.setVegPackLossRate(netPickupPercent(netPickup.subtract(mapBd(m, "vegPackUsageWeight")), netPickup));
        r.setVegDamagedPortionCount(mapInt(m, "vegDamagedPortionCount"));
        r.setVegDamageRate(percent2OrZero(mapBd(m, "vegDamagedPortionCount"), mapBd(m, "vegProducedPortionCount")));
        r.setVegProductionLossWeight(scale3(mapBd(m, "vegProductionLossWeight")));
        r.setVegProductionLossRate(netPickupPercent(r.getVegProductionLossWeight(), netPickup));
        r.setVegSpecQualifiedRate(percent2OrZero(mapBd(m, "vegSpecQualifiedCount"), mapBd(m, "vegStandardPortionCount")));
        r.setVegAvgOverspecPercent(divideOrZero(mapBd(m, "vegOverspecPercentSum"), mapBd(m, "vegOverspecPortionCount"), 2));
        r.setVegUndamagedQualifiedCount(mapInt(m, "vegUndamagedQualifiedCount"));

        YearMonth month = YearMonth.from(firstDay);
        LocalDate today = businessToday();
        boolean currentMonth = month.equals(YearMonth.from(today));
        LocalDate limit = currentMonth ? today : month.atEndOfMonth();
        var stock = aggregateMapper.selectLatestVegStockTotals(tenantId, currentMonth ? firstDay : limit, limit);
        r.setVegEndStockWeight(stock == null ? null : scale3(stock.stockWeight()));
        r.setVegEndStockDate(stock == null ? null : stock.statDate());
    }

    private BigDecimal vegStockWeight(String tenantId, LocalDate from, LocalDate to) {
        var stock = aggregateMapper.selectLatestVegStockTotals(tenantId, from, to);
        return stock == null ? null : scale3(stock.stockWeight());
    }

    // ============================================================
    //  Read end
    // ============================================================

    @Override
    public List<WarehouseIndicatorRecordVo> listDaily(LocalDate from, LocalDate to) {
        LambdaQueryWrapper<WarehouseIndicatorRecord> q = new LambdaQueryWrapper<WarehouseIndicatorRecord>()
            .ge(from != null, WarehouseIndicatorRecord::getStatDate, from)
            .le(to != null, WarehouseIndicatorRecord::getStatDate, to)
            .orderByDesc(WarehouseIndicatorRecord::getStatDate);
        return indicatorMapper.selectVoList(q);
    }

    @Override
    public List<WarehouseCroppRecordVo> listCrop(LocalDate from, LocalDate to) {
        String tenantId = currentTenant();
        return croppMapper.selectCroppList(tenantId,
            from == null ? null : from.toString(),
            to == null ? null : to.toString());
    }

    @Override
    public List<WarehouseMonthlyRecordVo> listMonthly(String monthFrom, String monthTo) {
        LambdaQueryWrapper<WarehouseMonthlyRecord> q = new LambdaQueryWrapper<WarehouseMonthlyRecord>()
            .ge(monthFrom != null && !monthFrom.isBlank(), WarehouseMonthlyRecord::getStatMonth, monthFrom)
            .le(monthTo != null && !monthTo.isBlank(), WarehouseMonthlyRecord::getStatMonth, monthTo)
            .orderByDesc(WarehouseMonthlyRecord::getStatMonth);
        List<WarehouseMonthlyRecordVo> records = monthlyMapper.selectVoList(q);
        YearMonth currentMonth = YearMonth.from(businessToday());
        // 跨月后，已关闭月份只能展示月末快照，即使月初日批尚未重算该月。
        for (var row : records) {
            YearMonth month = YearMonth.parse(row.getStatMonth());
            if (month.isBefore(currentMonth) && !month.atEndOfMonth().equals(row.getVegEndStockDate())) {
                row.setVegEndStockDate(null);
                row.setVegEndStockWeight(null);
            }
        }
        return records;
    }

    @Override
    public WarehouseAnnualRecordVo getAnnual(int year) {
        YearMonth firstMonth = YearMonth.of(year, 1);
        String tenantId = currentTenant();
        Map<String, Object> totals = aggregateMapper.sumAnnualFromMonthly(tenantId,
            firstMonth.format(MONTH_FMT), firstMonth.plusMonths(11).format(MONTH_FMT));
        BigDecimal loss = scale3(mapBd(totals, "cutLoss"));
        BigDecimal pickup = scale3(mapBd(totals, "cutLossPickupWeight"));
        LocalDate today = businessToday();
        // 本年最新有效快照可能在上月中旬，已结月份的月末指标可能为空。
        BigDecimal endStock = year == today.getYear()
            ? vegStockWeight(tenantId, firstMonth.atDay(1), today)
            : (BigDecimal) totals.get("vegEndStockWeight");
        return new WarehouseAnnualRecordVo(year, loss, pickup, percent2OrNull(loss, pickup),
            scale3(mapBd(totals, "vegRawInboundWeight")), scale3(mapBd(totals, "vegDeliveryOutWeight")),
            mapInt(totals, "slaughterCount"),
            divideOrZero(mapBd(totals, "barYieldRateSum"), mapBd(totals, "barYieldRateDays"), 2),
            percent2OrZero(mapBd(totals, "cutProductWeight"), mapBd(totals, "cutBarWeight")),
            endStock, percent2OrZero(mapBd(totals, "vegDamagedPortionCount"), mapBd(totals, "vegProducedPortionCount")));
    }

    // ============================================================
    //  helpers
    // ============================================================

    private static String currentTenant() {
        String t = TenantHelper.getTenantId();
        return (t == null || t.isBlank()) ? DEFAULT_TENANT : t;
    }

    private static Map<Long, Map<String, Object>> indexByCropId(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> m = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) {
            Object cid = row.get("cropId");
            if (cid instanceof Number n) {
                m.put(n.longValue(), row);
            }
        }
        return m;
    }

    /** Map 取 BigDecimal（缺省 0）。 */
    private static BigDecimal mapBd(Map<String, Object> m, String key) {
        if (m == null) {
            return BigDecimal.ZERO;
        }
        Object v = m.get(key);
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return BigDecimal.ZERO;
    }

    /** Map 取 int（缺省 0）。 */
    private static int mapInt(Map<String, Object> m, String key) {
        if (m == null) {
            return 0;
        }
        Object v = m.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    /** 3 位小数（重量落库统一精度，null → 0）。 */
    private static BigDecimal scale3(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * 比值 num/denom（非百分比），3 位小数；denom≤0 → null（无意义不造假，配 @TableField ALWAYS 覆盖旧值）。
     * 防脏数据：denom≤0 不除零。
     */
    private static BigDecimal divideOrNull(BigDecimal num, BigDecimal denom) {
        if (num == null || denom == null || denom.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return num.divide(denom, 3, RoundingMode.HALF_UP);
    }

    /**
     * 百分比 num/denom×100，3 位小数；denom≤0 → null。分子负值（如发往−接收为负的异常态）保留原值不截断。
     */
    private static BigDecimal pctOrNull(BigDecimal num, BigDecimal denom) {
        if (num == null || denom == null || denom.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return num.multiply(new BigDecimal("100")).divide(denom, 3, RoundingMode.HALF_UP);
    }

    private static BigDecimal divideOrZero(BigDecimal numerator, BigDecimal denominator, int scale) {
        return denominator == null || denominator.signum() <= 0 ? BigDecimal.ZERO.setScale(scale)
            : numerator.divide(denominator, scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent2OrZero(BigDecimal numerator, BigDecimal denominator) {
        return divideOrZero(numerator.movePointRight(2), denominator, 2);
    }

    /** 跨期退回可能形成负净领用，按有符号的账面净额计算。 */
    private static BigDecimal netPickupPercent(BigDecimal numerator, BigDecimal netPickup) {
        return netPickup.signum() == 0 ? BigDecimal.ZERO.setScale(2)
            : numerator.movePointRight(2).divide(netPickup, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent2OrNull(BigDecimal numerator, BigDecimal denominator) {
        return denominator.signum() <= 0 ? null
            : numerator.movePointRight(2).divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
