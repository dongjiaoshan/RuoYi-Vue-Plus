package org.dromara.djs.plant.market.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 果蔬上市计划「状态」计算（V6-R158）。
 *
 * <p>状态不落库，每次查询按上市日期 / 下架日期与<b>当天</b>现算。甲方原文五档：</p>
 * <ol>
 *   <li>上市日期 − 当前日期 &gt; 30 天 → 待上市</li>
 *   <li>上市日期 − 当前日期 ≤ 30 天 → 即将上市</li>
 *   <li>当前日期 &gt; 上市日期 且 下架日期 − 当前日期 &gt; 15 天 → 上市中</li>
 *   <li>当前日期 &gt; 上市日期 且 下架日期 − 当前日期 ≤ 15 天 → 即将下市</li>
 *   <li>当前日期 &gt; 下架日期 → 已下架</li>
 * </ol>
 *
 * <p>原文五条按字面并不互斥（第 2 条的「≤30 天」在已经过了上市日期时同样成立，第 4 条的「≤15 天」
 * 在已经过了下架日期时同样成立），所以判定顺序固定为 <b>5 → 3/4 → 1/2</b>：先看有没有过下架日期，
 * 再看有没有过上市日期，最后才落到上市前的两档。这样每条计划只会落到一个状态。</p>
 *
 * <p>边界按原文取字面值：第 3/4 条要求「当前日期<b>大于</b>上市日期」，所以当天正好等于上市日期时
 * 落在第 2 条 → 即将上市；当天正好等于下架日期时落在第 4 条 → 即将下市。</p>
 *
 * @author djs
 */
public final class MarketStatusCalculator {

    /** 待上市：离上市还有 30 天以上。 */
    public static final String PENDING = "pending";

    /** 即将上市：离上市 30 天以内（含当天等于上市日期）。 */
    public static final String UPCOMING = "upcoming";

    /** 上市中：已过上市日期，且离下架还有 15 天以上。 */
    public static final String ON_SALE = "on_sale";

    /** 即将下市：已过上市日期，且离下架 15 天以内（含当天等于下架日期）。 */
    public static final String ENDING = "ending";

    /** 已下架：已过下架日期。 */
    public static final String OFF_SHELF = "off_shelf";

    /** 「待上市 / 即将上市」的分界（天）。 */
    private static final long UPCOMING_DAYS = 30L;

    /** 「上市中 / 即将下市」的分界（天）。 */
    private static final long ENDING_DAYS = 15L;

    private MarketStatusCalculator() {
    }

    /**
     * 算状态码。
     *
     * <p>上市日期为空（该计划一条采摘明细都没排）时返回 {@code null}，列表 / 导出显 {@code -} ——
     * 没有上市日期就无从判断任何一档，不猜。下架日期单独为空时仍可判前三档，只是永远进不了「即将下市 / 已下架」。</p>
     *
     * @param beginDate 上市日期（实际优先、计划兜底后的有效值，可空）
     * @param endDate   下架日期（同上，可空）
     * @param today     当天
     * @return 状态码，或 {@code null}（上市日期缺失）
     */
    public static String resolve(LocalDate beginDate, LocalDate endDate, LocalDate today) {
        if (beginDate == null || today == null) {
            return null;
        }
        if (endDate != null && today.isAfter(endDate)) {
            return OFF_SHELF;
        }
        if (today.isAfter(beginDate)) {
            if (endDate == null) {
                // 有上市日期没下架日期：已经开卖且没有结束日可比，只能是上市中
                return ON_SALE;
            }
            return ChronoUnit.DAYS.between(today, endDate) > ENDING_DAYS ? ON_SALE : ENDING;
        }
        return ChronoUnit.DAYS.between(today, beginDate) > UPCOMING_DAYS ? PENDING : UPCOMING;
    }

    /**
     * 状态码 → 中文名（仅导出用；admin 页面走前端 i18n，不读这里）。
     *
     * @param status 状态码（可空）
     * @return 中文名，未知 / 空码返回 {@code null}
     */
    public static String name(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PENDING -> "待上市";
            case UPCOMING -> "即将上市";
            case ON_SALE -> "上市中";
            case ENDING -> "即将下市";
            case OFF_SHELF -> "已下架";
            default -> null;
        };
    }
}
