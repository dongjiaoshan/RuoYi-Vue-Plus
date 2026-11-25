package org.dromara.djs.plant.indicator.service;

import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.DockDispatch;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.DockReceipt;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.Inbound;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 月台不逐篮收货。只归入单据能证明由采摘直送的那部分实收重量。 */
final class DockFirstInbound {

    private DockFirstInbound() {
    }

    static List<Result> match(List<DockDispatch> dispatches, List<DockReceipt> receipts, LocalDate from) {
        var shipments = dispatches.stream().sorted(Comparator.comparing(DockDispatch::time)).iterator();
        DockDispatch next = shipments.hasNext() ? shipments.next() : null;
        Map<Key, Pending> pending = new HashMap<>();
        List<Result> result = new ArrayList<>();
        for (DockReceipt receipt : receipts.stream()
            .sorted(Comparator.comparing(DockReceipt::time).thenComparingLong(DockReceipt::id)).toList()) {
            while (next != null && !next.time().isAfter(receipt.time())) {
                var key = new Key(next.plotId(), next.cropId(), next.productId());
                pending.computeIfAbsent(key, ignored -> new Pending()).dispatch(next);
                next = shipments.hasNext() ? shipments.next() : null;
            }
            var key = new Key(receipt.plotId(), receipt.cropId(), receipt.productId());
            Pending stock = pending.get(key);
            if (stock == null && receipt.weight().add(receipt.loss()).signum() > 0
                && (key.cropId() == null || key.productId() == null)) {
                // 残缺收货可能已经占用这些来源。清除不能排除的余额，避免下一笔再错认成直送。
                pending.keySet().removeIf(key::couldBeSameStock);
            }
            Result matched = stock == null ? Result.unmatched(receipt) : stock.receive(receipt);
            if (!matched.inbound().date().isBefore(from)) {
                result.add(matched);
            }
        }
        return result;
    }

    private record Key(Long plotId, Long cropId, Long productId) {
        private boolean couldBeSameStock(Key other) {
            return Objects.equals(plotId, other.plotId)
                && (cropId == null || cropId.equals(other.cropId))
                && (productId == null || productId.equals(other.productId));
        }
    }

    record Result(Inbound inbound, boolean unmatched) {
        private static Result unmatched(DockReceipt receipt) {
            return from(receipt, BigDecimal.ZERO, receipt.weight().signum() > 0);
        }

        private static Result from(DockReceipt receipt, BigDecimal weight, boolean unmatched) {
            return new Result(new Inbound(receipt.time().toLocalDate(), receipt.plotId(), receipt.cropId(), null, weight),
                unmatched);
        }
    }

    private static final class Pending {
        private BigDecimal total = BigDecimal.ZERO;
        private BigDecimal minDirect = BigDecimal.ZERO;
        private BigDecimal maxDirect = BigDecimal.ZERO;
        private LocalDate receiptDay;
        private BigDecimal maxOtherForDay = BigDecimal.ZERO;

        private void dispatch(DockDispatch dispatch) {
            total = total.add(dispatch.weight());
            if ("direct".equals(dispatch.origin())) {
                minDirect = minDirect.add(dispatch.weight());
            } else if (dispatch.time().toLocalDate().equals(receiptDay)) {
                maxOtherForDay = maxOtherForDay.add(dispatch.weight());
            }
            if (!"transfer".equals(dispatch.origin())) {
                maxDirect = maxDirect.add(dispatch.weight());
            }
        }

        private Result receive(DockReceipt receipt) {
            BigDecimal used = receipt.weight().add(receipt.loss());
            if (used.compareTo(total) > 0) {
                // 缺到货单，库存链在这笔收货处断开。之后只使用新到货的证据。
                total = BigDecimal.ZERO;
                minDirect = BigDecimal.ZERO;
                maxDirect = BigDecimal.ZERO;
                maxOtherForDay = BigDecimal.ZERO;
                return Result.unmatched(receipt);
            }
            LocalDate date = receipt.time().toLocalDate();
            if (!date.equals(receiptDay)) {
                receiptDay = date;
                maxOtherForDay = total.subtract(minDirect);
            }
            // 日内先用所有可能的非直送量覆盖实收，再尽量用直送量覆盖损耗。
            // 即使两笔实收均不能逐笔定位，合计超过日内非直送量的部分仍可证明是直送。
            BigDecimal dailyDirect = receipt.weight().subtract(maxOtherForDay).max(BigDecimal.ZERO);
            BigDecimal confirmedForReceipt = minDirect.subtract(total.subtract(receipt.weight())).max(BigDecimal.ZERO);
            BigDecimal possibleForReceipt = maxDirect.min(receipt.weight());

            total = total.subtract(used);
            maxOtherForDay = maxOtherForDay.subtract(receipt.weight()).max(BigDecimal.ZERO).min(total);
            minDirect = minDirect.subtract(used).max(BigDecimal.ZERO);
            maxDirect = maxDirect.min(total);
            return Result.from(receipt, dailyDirect, confirmedForReceipt.compareTo(possibleForReceipt) < 0);
        }
    }
}
