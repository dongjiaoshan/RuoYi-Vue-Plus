package org.dromara.djs.plant.market.util;

import org.dromara.djs.plant.common.util.DateWindowStatusCalculator;

/**
 * 果蔬上市计划「状态」中文名（仅导出用；admin 页面走前端 i18n {@code marketPlan.status.*}，不读这里）。
 *
 * <p>判定在 {@link DateWindowStatusCalculator}，本类只负责把五档码翻成上市域的说法。</p>
 *
 * @author djs
 */
public final class MarketStatusLabel {

    private MarketStatusLabel() {
    }

    /**
     * 状态码 → 中文名。
     *
     * @param status 状态码（可空）
     * @return 中文名，未知 / 空码返回 {@code null}
     */
    public static String name(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case DateWindowStatusCalculator.PENDING -> "待上市";
            case DateWindowStatusCalculator.UPCOMING -> "即将上市";
            case DateWindowStatusCalculator.ON_SALE -> "上市中";
            case DateWindowStatusCalculator.ENDING -> "即将下市";
            case DateWindowStatusCalculator.OFF_SHELF -> "已下架";
            default -> null;
        };
    }
}
