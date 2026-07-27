package org.dromara.djs.warehouse.loss.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.loss.domain.LossFlow;
import org.dromara.djs.warehouse.loss.mapper.LossFlowMapper;
import org.dromara.djs.warehouse.loss.mapper.ProductionLossAggregateMapper;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.dromara.djs.warehouse.loss.service.IProductionLossService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产损耗日聚合实现（邓博最终口径，R25 全品类）。
 *
 * <p>逐产品残差 = 领用 − 退回 − 录入损耗 − 饲喂 − 打包生产使用量（&gt;0 才逐产品落一条 {@code loss_type='production_loss'}
 * 到损耗总览），覆盖 {@code belong_type IN ('vegetable','pork','egg','dry_good')}（自产果蔬 / 猪肉 / 鸡蛋 / 干货；
 * 外购商品/包材天然排除；白条走预冷/分割损耗专口径、礼盒为组合品，均排除）。逐产品逐行、各带自己单位，无跨产品跨单位求和；
 * 饲喂 feed_out 仅自产果蔬有值，非果蔬品类恒 0。<b>仅记损耗总览表（{@code t_warehouse_loss_flow}），不写聚合日表</b>
 * （日表净菜损耗率仍单出果蔬，见 {@code WarehouseStatServiceImpl}）。
 * 由 {@code WarehouseStatServiceImpl#aggregate} 驱动（先于 cropp 聚合跑，production_loss 对 cropp 净菜损耗率即时新鲜），
 * 原独立 ProductionLossAggregateJob 已撤。</p>
 *
 * @author djs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionLossServiceImpl implements IProductionLossService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String DEFAULT_TENANT = "1001";
    private static final String LOSS_TYPE_PRODUCTION = "production_loss";

    private final ProductionLossAggregateMapper aggregateMapper;
    private final LossFlowMapper lossFlowMapper;
    private final ILossFlowService lossFlowService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String aggregate(LocalDate targetDate) {
        LocalDate date = targetDate != null ? targetDate : LocalDate.now(ZONE).minusDays(1);
        String tenantId = currentTenant();
        String statDate = date.toString();

        // 幂等：先软删该日已算的生产损耗行，供重跑覆盖（MP @TableLogic 软删 + 拦截器补 tenant）
        lossFlowMapper.delete(new LambdaQueryWrapper<LossFlow>()
            .eq(LossFlow::getLossType, LOSS_TYPE_PRODUCTION)
            .apply("DATE(loss_date) = DATE({0})", statDate));

        // 聚合五项量：[pickOut, returnIn, feedOut, manualLoss, packUsage]（belong_type 果蔬/猪肉/鸡蛋/干货，逐产品）
        Map<Long, BigDecimal[]> byProduct = new LinkedHashMap<>();
        for (Map<String, Object> r : aggregateMapper.selectProductFlowAgg(tenantId, statDate)) {
            Long pid = toLong(r.get("productId"));
            if (pid == null) {
                continue;
            }
            BigDecimal[] a = rowOf(byProduct, pid);
            a[0] = toDec(r.get("pickOut"));
            a[1] = toDec(r.get("returnIn"));
            a[2] = toDec(r.get("feedOut"));
        }
        for (Map<String, Object> r : aggregateMapper.selectProductManualLoss(tenantId, statDate)) {
            Long pid = toLong(r.get("productId"));
            if (pid == null) {
                continue;
            }
            rowOf(byProduct, pid)[3] = toDec(r.get("manualLoss"));
        }
        for (Map<String, Object> r : aggregateMapper.selectProductPackUsage(tenantId, statDate)) {
            Long pid = toLong(r.get("productId"));
            if (pid == null) {
                continue;
            }
            rowOf(byProduct, pid)[4] = toDec(r.get("packUsage"));
        }

        // admin row109：零点任务处理的是上一自然日，业务损耗时间记在上一日结束，
        // 不能显示为任务触发日/目标日 00:00:00。
        Date lossDate = Date.from(date.plusDays(1).atStartOfDay(ZONE).minusSeconds(1).toInstant());
        int written = 0;
        for (Map.Entry<Long, BigDecimal[]> e : byProduct.entrySet()) {
            BigDecimal[] a = e.getValue();
            // 生产损耗 = 领用 − 退回 − 录入损耗 − 饲喂 − 打包生产使用量（邓博最终口径，逐产品各带自己单位；非果蔬品类饲喂恒 0）
            BigDecimal residual = a[0].subtract(a[1]).subtract(a[3]).subtract(a[2]).subtract(a[4]);
            if (residual.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LossFlow flow = new LossFlow();
            flow.setLossType(LOSS_TYPE_PRODUCTION);
            flow.setProductId(e.getKey());
            flow.setLossWeight(residual.setScale(3, RoundingMode.HALF_UP));
            flow.setLossDate(lossDate);
            flow.setSourceBizType("production");
            // productCode / productName / productUnit / belongType 由 record() 按 productId 回填快照
            if (lossFlowService.record(flow) != null) {
                written++;
            }
        }

        String msg = String.format("ok | tenant=%s | date=%s | products=%d | written=%d",
            tenantId, statDate, byProduct.size(), written);
        log.info("[production-loss-aggregate] {}", msg);
        return msg;
    }

    private BigDecimal[] rowOf(Map<Long, BigDecimal[]> m, Long pid) {
        return m.computeIfAbsent(pid, k -> new BigDecimal[]{
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
    }

    private String currentTenant() {
        String t = TenantHelper.getTenantId();
        return (t == null || t.isBlank()) ? DEFAULT_TENANT : t;
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        return v == null ? null : Long.valueOf(v.toString());
    }

    private BigDecimal toDec(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(v.toString());
    }
}
