package org.dromara.djs.warehouse.stat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.stat.domain.WarehouseCroppRecord;
import org.dromara.djs.warehouse.stat.domain.WarehouseIndicatorRecord;
import org.dromara.djs.warehouse.stat.domain.WarehouseMonthlyRecord;
import org.dromara.djs.warehouse.stat.domain.vo.WarehouseCroppRecordVo;
import org.dromara.djs.warehouse.stat.domain.vo.WarehouseIndicatorRecordVo;
import org.dromara.djs.warehouse.stat.domain.vo.WarehouseMonthlyRecordVo;
import org.dromara.djs.warehouse.stat.mapper.WarehouseCroppRecordMapper;
import org.dromara.djs.warehouse.stat.mapper.WarehouseIndicatorRecordMapper;
import org.dromara.djs.warehouse.stat.mapper.WarehouseMonthlyRecordMapper;
import org.dromara.djs.warehouse.stat.mapper.WarehouseStatAggregateMapper;
import org.dromara.djs.warehouse.stat.service.IWarehouseStatService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 仓库统计指标聚合 + 只读查询实现（WMS-STAT-001，邓博 admin row16/17/18）。
 *
 * <p>聚合顺序：日表 + 作物日表 UPSERT → 月表汇总当月日表。月率从已落盘日表 Σ 回读，
 * 故日表必须先写。所有源表按自然日 {@code DATE(col)=statDate} 聚合，BigDecimal 统一 setScale(3)。</p>
 *
 * <h3>已拍口径</h3>
 * <ol>
 *   <li>白条出品率 = 白条总重/送宰总重×100（A1：row16 ÷屠宰头数疑笔误，统一对齐 row18 ÷送宰总重，待邓博知会）</li>
 *   <li>路损率 = (发往月台−月台接收)/发往月台×100（A2 日表，分母用发往=损耗占发出量）；作物表按 row17 (发往−接收)/接收×100</li>
 *   <li>所有损耗一律从 loss_flow 按 loss_type 取（防重复计）</li>
 *   <li>月台接收只算自产 receive_type=1</li>
 *   <li>作物日表维度 = crop_id</li>
 *   <li>分割率 = 分割产品总重/分割白条总重（B1：分母 = 分割白条总重 cut_record.pickup_weight，Kevin 确认）</li>
 *   <li>送宰总重 = 自产出栏送宰 + 外购毛重；屠宰头数 = burn 记录数</li>
 *   <li>作物净菜损耗（row17）生产/录入损耗按 product_id→crop(related_product) 归集（B5：plot 桥接漏 product 维损耗）</li>
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

    private static final String DEFAULT_TENANT = "1001";
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final WarehouseStatAggregateMapper aggregateMapper;
    private final WarehouseIndicatorRecordMapper indicatorMapper;
    private final WarehouseCroppRecordMapper croppMapper;
    private final WarehouseMonthlyRecordMapper monthlyMapper;

    // ============================================================
    //  Write end — aggregate
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String aggregate(LocalDate targetDate) {
        if (targetDate == null) {
            targetDate = LocalDate.now().minusDays(1); // T-1
        }
        String tenantId = currentTenant();
        String statDate = targetDate.toString();
        log.info("[WarehouseStat] start tenant={} date={}", tenantId, statDate);

        upsertDaily(tenantId, statDate);
        int croppRows = upsertCropp(tenantId, statDate);
        String month = YearMonth.from(targetDate).format(MONTH_FMT);
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

        // 屠宰 / 送宰段
        int slaughterCount = aggregateMapper.countSlaughter(tenantId, statDate);
        BigDecimal arrive = scale3(aggregateMapper.sumArriveWeight(tenantId, statDate));
        BigDecimal slaughterWeight = scale3(
            aggregateMapper.sumMarketingWeight(tenantId, statDate)
                .add(aggregateMapper.sumOutsourceWeight(tenantId, statDate)));
        r.setSlaughterCount(slaughterCount);
        r.setArriveWeight(arrive);
        r.setSlaughterWeight(slaughterWeight);
        r.setAvgSlaughterWeight(divideOrNull(slaughterWeight, new BigDecimal(slaughterCount)));
        r.setSlaughterRate(pctOrNull(arrive, slaughterWeight));

        // 白条段
        BigDecimal barTotal = scale3(aggregateMapper.sumBarTotalWeight(tenantId, statDate));
        r.setBarTotalWeight(barTotal);
        r.setAvgBarWeight(divideOrNull(barTotal, new BigDecimal(slaughterCount)));
        // 口径#1（A1）：白条出品率 = 白条总重 ÷ 送宰总重 × 100。
        // spec 日表 row16 写「÷屠宰头数」疑笔误（白条总重÷头数 = 白条均重，再×100 量纲重复、与上面 avgBarWeight 同义）；
        // 对齐邓博自己的 row18 月表「÷送宰总重」+ 行业标准（出品率 = 产出重/投入重），此处取 ÷送宰总重，待邓博知会确认。
        r.setBarYieldRate(pctOrNull(barTotal, slaughterWeight));

        // 分割段
        BigDecimal cutProduct = scale3(aggregateMapper.sumCutProductWeight(tenantId, statDate));
        BigDecimal cutBar = scale3(aggregateMapper.sumCutBarWeight(tenantId, statDate));
        r.setCutBarCount(aggregateMapper.countCutBar(tenantId, statDate));
        r.setPrecoolLoss(scale3(aggregateMapper.sumLossByType(tenantId, statDate, "precool_loss")));
        r.setCutProductWeight(cutProduct);
        r.setCutBarWeight(cutBar);
        // 口径#6（B1）：分割率 = 分割产品总重 ÷ 分割白条总重 × 100。
        // 分母用 cutBarWeight(分割白条总重 = Σ cut_record.pickup_weight) = Kevin 确认口径（非分割产品总重自身），固化不改。
        r.setCutRate(pctOrNull(cutProduct, cutBar));
        r.setCutLoss(scale3(aggregateMapper.sumLossByType(tenantId, statDate, "cut_loss")));

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

        // 净菜生产段
        BigDecimal prodPick = scale3(aggregateMapper.sumProdPickWeight(tenantId, statDate));
        BigDecimal prodReturn = scale3(aggregateMapper.sumProdReturnWeight(tenantId, statDate));
        BigDecimal manualLoss = scale3(aggregateMapper.sumLossByType(tenantId, statDate, "manual_loss"));
        BigDecimal feedTotal = scale3(aggregateMapper.sumFeedWeightTotal(tenantId, statDate));
        // 果蔬生产损耗（row16）= 残差口径（Kevin 拍板）：
        // prodLoss = max(0, 当日领用 − 退回 − 录入损耗 − 饲喂总重)。
        // production_loss 这个 loss_type 无数据（恒 0），生产损耗改由物料平衡残差反推：
        // 领用出去的原料，扣掉退回、扣掉人工录入损耗、扣掉转去饲喂的，剩下没入成品的差额即为生产损耗；
        // 领用<（退回+录入+饲喂）时残差为负，无业务意义，置 0。
        BigDecimal prodLoss = scale3(
            prodPick.subtract(prodReturn).subtract(manualLoss).subtract(feedTotal)
                .max(BigDecimal.ZERO));
        r.setProdPickWeight(prodPick);
        r.setProdLossWeight(prodLoss);
        // 净菜损耗率 = (生产损耗+录入损耗)/(生产领用−生产退回)×100（日表口径）
        BigDecimal netDenom = prodPick.subtract(prodReturn);
        r.setNetVegLossRate(pctOrNull(prodLoss.add(manualLoss), netDenom));

        upsertIndicatorRow(tenantId, statDate, r);
    }

    /** UPSERT 单条日表行（按 stat_date 查存在 → updateById / insert）。 */
    private void upsertIndicatorRow(String tenantId, String statDate, WarehouseIndicatorRecord r) {
        WarehouseIndicatorRecord existing = indicatorMapper.selectOne(
            new LambdaQueryWrapper<WarehouseIndicatorRecord>()
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
     * 月率用 Σ 分子÷Σ 分母（非日率平均）。
     */
    private void upsertMonthly(String tenantId, String month) {
        Map<String, Object> m = aggregateMapper.sumMonthlyFromDaily(tenantId, month);

        WarehouseMonthlyRecord r = new WarehouseMonthlyRecord();
        r.setStatMonth(month);
        r.setSlaughterCount(mapInt(m, "slaughterCount"));
        // 屠宰率 = Σ接收/Σ送宰×100
        r.setSlaughterRate(pctOrNull(mapBd(m, "sumArrive"), mapBd(m, "sumSlaughterWeight")));
        // 白条出品率 = Σ白条/Σ送宰×100
        r.setBarYieldRate(pctOrNull(mapBd(m, "sumBarTotal"), mapBd(m, "sumSlaughterWeight")));
        // 分割出品率 = Σ分割产品/Σ分割白条×100
        r.setCutYieldRate(pctOrNull(mapBd(m, "sumCutProduct"), mapBd(m, "sumCutBar")));

        WarehouseMonthlyRecord existing = monthlyMapper.selectOne(
            new LambdaQueryWrapper<WarehouseMonthlyRecord>()
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
        return monthlyMapper.selectVoList(q);
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
}
