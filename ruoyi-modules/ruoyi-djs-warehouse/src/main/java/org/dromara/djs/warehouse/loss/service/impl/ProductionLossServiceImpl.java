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
 * 生产损耗日聚合实现（row38，邓博）。
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

        // 聚合四项量：[pickOut, returnIn, feedOut, manualLoss]
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

        // 落库当日 00:00（损耗总览按 DATE(loss_date) 归日，取当日任意时刻即可）
        Date lossDate = Date.from(date.atStartOfDay(ZONE).toInstant());
        int written = 0;
        for (Map.Entry<Long, BigDecimal[]> e : byProduct.entrySet()) {
            BigDecimal[] a = e.getValue();
            // 生产损耗 = 产品领用 − 产品退回 − 产品录入损耗 − 产品饲喂（邓博 row38 口径）
            BigDecimal residual = a[0].subtract(a[1]).subtract(a[3]).subtract(a[2]);
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
        return m.computeIfAbsent(pid, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
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
