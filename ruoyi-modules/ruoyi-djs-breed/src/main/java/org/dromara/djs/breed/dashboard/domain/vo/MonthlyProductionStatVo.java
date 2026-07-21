package org.dromara.djs.breed.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 当月生产统计表（率 / 窝均 9 行）VO（FIX-MGMT-MP-BRD-001，原型"种猪 tab·当月生产统计表"，#7.8）。
 *
 * <p>本表 = 9 行率/窝均指标 × (当月 vs 上月)。区别于现有
 * {@code /monthly-comparison}（7 行头数快照）：本端点输出 9 行率/窝均口径，实时算。</p>
 *
 * <p>9 行口径：分娩率 / 断配间隔 / 返空流头数 / 平均非生产天数NPD / 总产仔数 /
 * 窝均总产仔 / 窝均活仔 / 窝均断奶 / 产房损失率（R73 删配怀率）。复用 BreedingAnnual 同套公式（#7.1-7.5）。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-BRD-001
 */
@Data
public class MonthlyProductionStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当月（yyyy-MM）。 */
    private String currentMonth;

    /** 上月（yyyy-MM）。 */
    private String previousMonth;

    /** 10 行率/窝均指标（顺序固定）。 */
    private List<StatRow> rows = new ArrayList<>();

    /**
     * 单行：中文指标名 + 当月值 + 上月值 + 单位提示。
     */
    @Data
    public static class StatRow implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 指标名（固定中文文案）。 */
        private String metric;

        /** 当月值。 */
        private BigDecimal current;

        /** 上月值。 */
        private BigDecimal previous;

        /** 单位/口径提示（如"%"、"天"、"头"、"头/窝"），前端拼显示。 */
        private String unit;

        public StatRow() {
        }

        public StatRow(String metric, BigDecimal current, BigDecimal previous, String unit) {
            this.metric = metric;
            this.current = current == null ? BigDecimal.ZERO : current;
            this.previous = previous == null ? BigDecimal.ZERO : previous;
            this.unit = unit;
        }
    }
}
