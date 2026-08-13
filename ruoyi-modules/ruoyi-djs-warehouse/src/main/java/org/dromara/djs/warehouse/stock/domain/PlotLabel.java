package org.dromara.djs.warehouse.stock.domain;

/**
 * 「地块」展示文案的唯一口径（V6 row92）。
 *
 * <p>甲方口径：「三期」不是真实地块，只是挂在库存 / 流水行上的一个标识 + 文案
 * （原话「三期目前无相应地块，只做文案显示」）。所以凡是要显示地块的地方，
 * 三期的货一律显示「三期」，而不是它底下那块真实地块的名字。</p>
 *
 * <p>本类是<b>导出件</b>的口径来源（{@code StockFlowVo#getPlotLabel} /
 * {@code LocationStockVo#getPlotLabel}）；<b>页面</b>的同一份规则在
 * {@code plus-ui/src/utils/plotTag.ts#formatPlotLabel}。两处必须同步改 ——
 * 不同步的后果是甲方拿导出对账时看到与页面不一致的地块，重报同一条问题。</p>
 *
 * @author djs
 * @since V6-R92
 */
public final class PlotLabel {

    /** 三期标识：1=是。 */
    private static final Integer THIRD_PHASE_YES = 1;

    /** 无地块可显示时的占位（与前端 formatPlotLabel 一致）。 */
    private static final String NONE = "-";

    /** 三期的展示文案（甲方原话就是这两个字）。 */
    private static final String THIRD_PHASE_TEXT = "三期";

    private PlotLabel() {
    }

    /**
     * @param thirdPhase 库存 / 流水行上的三期标识（存量行可能为 null）
     * @param plotName   真实地块名（无地块时为 null）
     * @return 三期 → 「三期」；否则有地块名 → 地块名；否则 {@code -}
     */
    public static String of(Integer thirdPhase, String plotName) {
        if (THIRD_PHASE_YES.equals(thirdPhase)) {
            return THIRD_PHASE_TEXT;
        }
        return plotName != null && !plotName.isBlank() ? plotName : NONE;
    }
}
