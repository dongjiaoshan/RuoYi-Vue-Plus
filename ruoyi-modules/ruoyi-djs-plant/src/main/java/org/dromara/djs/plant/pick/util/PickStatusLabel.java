package org.dromara.djs.plant.pick.util;

import org.dromara.djs.plant.common.util.DateWindowStatusCalculator;

/**
 * 采摘计划「状态」中文名（仅导出用；admin 页面走前端 i18n {@code pickPlan.status.*}，不读这里）。
 *
 * <p>判定在 {@link DateWindowStatusCalculator}（与果蔬上市计划同一套 30/15 天五档规则），
 * 本类只负责把五档码翻成采摘域的说法。</p>
 *
 * @author djs
 */
public final class PickStatusLabel {

    private PickStatusLabel() {
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
            case DateWindowStatusCalculator.PENDING -> "待采摘";
            case DateWindowStatusCalculator.UPCOMING -> "即将采摘";
            case DateWindowStatusCalculator.ON_SALE -> "采摘中";
            case DateWindowStatusCalculator.ENDING -> "即将结束采摘";
            case DateWindowStatusCalculator.OFF_SHELF -> "完成采摘";
            default -> null;
        };
    }
}
