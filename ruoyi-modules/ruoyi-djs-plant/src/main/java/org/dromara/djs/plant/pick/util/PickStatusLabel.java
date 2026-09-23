package org.dromara.djs.plant.pick.util;

import org.dromara.djs.plant.common.util.DateWindowStatusCalculator;

/**
 * 采摘计划「作物采摘期状态」中文名（仅导出用；admin 页面走前端 i18n {@code pickPlan.status.*}，不读这里）。
 *
 * <p>判定在 {@link DateWindowStatusCalculator}（与果蔬上市计划同一套 30/15 天五档规则），
 * 本类只负责把五档码翻成采摘域的说法。叫法刻意避开「采摘中 / 完成采摘」——那是抽屉里落库的
 * {@code harvest_status}（工人实际开采后写入），这里是按日期窗口现算的，两者同名会被当成一回事。</p>
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
            case DateWindowStatusCalculator.PENDING -> "未到采摘期";
            case DateWindowStatusCalculator.UPCOMING -> "临近采摘期";
            case DateWindowStatusCalculator.ON_SALE -> "采摘期内";
            case DateWindowStatusCalculator.ENDING -> "临近采摘末期";
            case DateWindowStatusCalculator.OFF_SHELF -> "已过采摘期";
            default -> null;
        };
    }
}
