package org.dromara.djs.breed.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 月度对比 VO（BRD-DASH-001）。
 *
 * <p>当月 vs 上月 7 项 KPI 对比 + 颜色字段（trend）。</p>
 *
 * <p><b>颜色规则（国内畜牧惯例反直觉，doc/06 强调）</b>：
 * <ul>
 *   <li>{@code trend = "better"} → fe 显示红色（数值更好）</li>
 *   <li>{@code trend = "worse"}  → fe 显示绿色（数值更差）</li>
 *   <li>{@code trend = "flat"}   → fe 显示黑色（持平）</li>
 * </ul>
 * 不要在 fe 自己判断红绿，be 已根据"数值上升好还是下降好"决定 trend；fe 只做色彩映射。</p>
 *
 * @author djs
 * @since BRD-DASH-001
 */
@Data
public class MonthlyComparisonVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计月（YYYY-MM）。 */
    private String currentMonth;
    /** 上一个统计月（YYYY-MM）。 */
    private String previousMonth;

    /** 7 项 KPI 的对比明细，按命名固定 7 项；前端按字段名取数据。 */
    private KpiCompare introduceCount;
    private KpiCompare bornCount;
    private KpiCompare weanedCount;
    private KpiCompare deathCount;
    private KpiCompare cullingCount;
    private KpiCompare marketingCount;
    private KpiCompare marketingWeight;

    /**
     * 单项 KPI 当月/上月/差值/趋势。
     */
    @Data
    public static class KpiCompare implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 当月值（Integer 或 BigDecimal，fe 按类型解析）。 */
        private BigDecimal current;
        /** 上月值。 */
        private BigDecimal previous;
        /** 差值（current - previous，可负）。 */
        private BigDecimal diff;
        /** 趋势字符串（better/worse/flat，fe 据此映射红/绿/黑）。 */
        private String trend;
    }
}
