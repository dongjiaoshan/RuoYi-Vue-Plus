package org.dromara.djs.common.supplier.api;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 供应商交易统计载体（轻量聚合结果）。
 *
 * <p>{@link SupplierDealProvider#aggregateStatsBySupplierIds(java.util.Collection)} 批量聚合的单个供应商结果，
 * 替代「捞全部明细行回 Java 内存数 size + reduce 求和」的 N+1 反模式，由 DB 直接 {@code COUNT(*)} / {@code SUM(...)}
 * 聚合后只回传两个标量。</p>
 *
 * <p>{@code dealCount} = 该供应商交易次数（明细行数）；{@code purchaseQty} = 购入量合计（各明细 dealQuantity 之和）。
 * 同一供应商可能命中多个来源（药品入库 + 物资入库），service 端用 {@link #plus(SupplierDealStat)} 合并。</p>
 *
 * @param dealCount   交易次数（≥ 0）
 * @param purchaseQty 购入量合计（非 null；无数据为 {@link BigDecimal#ZERO}）
 * @author djs
 */
public record SupplierDealStat(int dealCount, BigDecimal purchaseQty) {

    /** 零值常量：无任何交易的供应商回填用。 */
    public static final SupplierDealStat ZERO = new SupplierDealStat(0, BigDecimal.ZERO);

    public SupplierDealStat {
        purchaseQty = purchaseQty == null ? BigDecimal.ZERO : purchaseQty;
    }

    /**
     * 合并两个来源的统计（次数相加、购入量相加）。
     *
     * @param other 另一来源的统计（null 视为零值）
     * @return 合并后的新实例（不可变，原对象不变）
     */
    public SupplierDealStat plus(SupplierDealStat other) {
        if (other == null) {
            return this;
        }
        return new SupplierDealStat(
            this.dealCount + other.dealCount,
            this.purchaseQty.add(Objects.requireNonNullElse(other.purchaseQty, BigDecimal.ZERO))
        );
    }

}
