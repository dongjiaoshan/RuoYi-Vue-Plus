package org.dromara.djs.warehouse.tare.domain;

import org.dromara.common.core.exception.ServiceException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 一次称重的秤读数（D-0147 ③）：称重 {@code gross} 与去皮重量 {@code tare}，实重 = gross − tare。
 *
 * <p>六个称重页（猪只入库 / 白条分割出库 / 果蔬入库 / 果蔬出库 / 白条分割管理 / 白条出库领用）提交时
 * 除实重外另带这两个数，落在承载该次称重实重的出入库流水行（{@code t_warehouse_stock_flow.gross_weight /
 * tare_weight}）上，领用另落分割单（{@code t_warehouse_pig_cut_record}），方便事后追查当时秤读数与皮重。</p>
 *
 * <p>契约：两者都不传 = 旧客户端，落库 NULL；都传时必须满足 {@code |gross − tare − 实重| ≤ 0.001kg}；
 * 只传一个、负数、超过三位小数、对不上实重一律拒绝。</p>
 */
public record ScaleReading(BigDecimal grossWeight, BigDecimal tareWeight) {

    /** 称重 − 皮重 与实重的允许误差（kg）。 */
    static final BigDecimal TOLERANCE = new BigDecimal("0.001");

    /**
     * 校验并构造秤读数。
     *
     * @param gross 称重（kg，可空）
     * @param tare  去皮重量（kg，可空；不去皮为 0）
     * @param net   本次提交的实重（kg）
     * @return 两者都未传 → {@code null}；否则返回三位小数规范化后的读数
     */
    public static ScaleReading of(BigDecimal gross, BigDecimal tare, BigDecimal net) {
        if (gross == null && tare == null) {
            return null;
        }
        if (gross == null || tare == null) {
            throw new ServiceException("称重和去皮重量须同时提交");
        }
        if (gross.signum() < 0 || tare.signum() < 0) {
            throw new ServiceException("称重和去皮重量不能为负数");
        }
        if (gross.stripTrailingZeros().scale() > 3 || tare.stripTrailingZeros().scale() > 3) {
            throw new ServiceException("称重和去皮重量最多三位小数");
        }
        if (net == null) {
            throw new ServiceException("提交称重和去皮重量时须同时提交实重");
        }
        if (gross.subtract(tare).subtract(net).abs().compareTo(TOLERANCE) > 0) {
            throw new ServiceException("称重 − 去皮重量与实重不一致（称重 " + plain(gross) + "kg，去皮 "
                + plain(tare) + "kg，实重 " + plain(net) + "kg），请重新称重");
        }
        return new ScaleReading(gross.setScale(3, RoundingMode.UNNECESSARY), tare.setScale(3, RoundingMode.UNNECESSARY));
    }

    /** 幂等指纹用的规范串（两位读数各自去尾零）。 */
    public String fingerprint() {
        return plain(grossWeight) + '/' + plain(tareWeight);
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
