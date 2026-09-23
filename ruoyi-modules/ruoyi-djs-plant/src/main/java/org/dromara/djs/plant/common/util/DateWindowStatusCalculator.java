package org.dromara.djs.plant.common.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 「日期窗口五档状态」计算：给一个 (开始日期, 结束日期) 窗口和当天，算出它落在五档中的哪一档。
 *
 * <p>状态不落库，每次查询现算。甲方在果蔬上市计划与采摘计划两处给的是<b>同一套</b>规则
 * （同样的 30 天 / 15 天分界、同样的五档、同样的「当前日期大于开始日期」边界），
 * 只是两处叫法不同，所以判定只有这一份，中文名由各域的 Label 类各给一套：</p>
 * <ol>
 *   <li>开始日期 − 当前日期 &gt; 30 天 → {@link #PENDING}（上市：待上市／采摘：未到采摘期）</li>
 *   <li>开始日期 − 当前日期 ≤ 30 天 → {@link #UPCOMING}（即将上市／临近采摘期）</li>
 *   <li>当前日期 &gt; 开始日期 且 结束日期 − 当前日期 &gt; 15 天 → {@link #ON_SALE}（上市中／采摘期内）</li>
 *   <li>当前日期 &gt; 开始日期 且 结束日期 − 当前日期 ≤ 15 天 → {@link #ENDING}（即将下市／临近采摘末期）</li>
 *   <li>当前日期 &gt; 结束日期 → {@link #OFF_SHELF}（已下架／已过采摘期）</li>
 * </ol>
 *
 * <p>原文五条按字面并不互斥（第 2 条的「≤30 天」在已经过了开始日期时同样成立，第 4 条的「≤15 天」
 * 在已经过了结束日期时同样成立），所以判定顺序固定为 <b>5 → 3/4 → 1/2</b>：先看有没有过结束日期，
 * 再看有没有过开始日期，最后才落到开始前的两档。这样每行只会落到一个状态。</p>
 *
 * <p>边界按原文取字面值：第 3/4 条要求「当前日期<b>大于</b>开始日期」，所以当天正好等于开始日期时
 * 落在第 2 条 → 即将；当天正好等于结束日期时落在第 4 条 → 即将结束。</p>
 *
 * <p>状态码沿用上市域的字面（{@code on_sale / off_shelf}）：这串码已经进了果蔬上市计划的前端 i18n key
 * 与导出，改码是破坏性变更。采摘域复用同一串码、只换中文名。</p>
 *
 * @author djs
 */
public final class DateWindowStatusCalculator {

    /** 第 1 档：离开始还有 30 天以上。 */
    public static final String PENDING = "pending";

    /** 第 2 档：离开始 30 天以内（含当天等于开始日期）。 */
    public static final String UPCOMING = "upcoming";

    /** 第 3 档：已过开始日期，且离结束还有 15 天以上。 */
    public static final String ON_SALE = "on_sale";

    /** 第 4 档：已过开始日期，且离结束 15 天以内（含当天等于结束日期）。 */
    public static final String ENDING = "ending";

    /** 第 5 档：已过结束日期。 */
    public static final String OFF_SHELF = "off_shelf";

    /** 五档码，按「生命周期先后」排列；遍历统计时用它保证不漏档、不多档。 */
    public static final String[] CODES = {PENDING, UPCOMING, ON_SALE, ENDING, OFF_SHELF};

    /** 第 1 / 2 档的分界（天）。 */
    private static final long UPCOMING_DAYS = 30L;

    /** 第 3 / 4 档的分界（天）。 */
    private static final long ENDING_DAYS = 15L;

    private DateWindowStatusCalculator() {
    }

    /**
     * 算状态码。
     *
     * <p>开始日期为空（该行一条明细都没排）时返回 {@code null}，列表 / 导出显 {@code -} ——
     * 没有开始日期就无从判断任何一档，不猜。结束日期单独为空时仍可判前三档，
     * 只是永远进不了第 4 / 5 档。</p>
     *
     * @param beginDate 窗口开始日期（可空）
     * @param endDate   窗口结束日期（可空）
     * @param today     当天
     * @return 状态码，或 {@code null}（开始日期缺失）
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
                // 有开始日期没结束日期：已经开始且没有结束日可比，只能是第 3 档
                return ON_SALE;
            }
            return ChronoUnit.DAYS.between(today, endDate) > ENDING_DAYS ? ON_SALE : ENDING;
        }
        return ChronoUnit.DAYS.between(today, beginDate) > UPCOMING_DAYS ? PENDING : UPCOMING;
    }
}
