package org.dromara.djs.warehouse.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 猪只分割效能管理 VO（mp 仓库管理看板 tab1，对齐原型 404fcd9b）。
 *
 * <p>消费 WMS-STAT-001 落盘表：年度 KPI 汇总当年日表 {@code t_warehouse_indicator_record}；
 * 月度趋势取月表 {@code t_warehouse_monthly_record}（柱=出栏头数 + 3 折线出品率）；
 * 日矩阵取近 N 日日表（指标行 × 日期列 + 累计列）。无数据时各段 0 / 空列表，不抛错。</p>
 *
 * @author djs
 * @since WMS-DASH-MP-001
 */
@Data
public class WarehousePorkEfficiencyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计年份。 */
    private Integer year;

    // ====== 年度屠宰指标统计 4 KPI（原型「年度屠宰指标统计」）======

    /** 送宰头数（当年日屠宰头数之和）。 */
    private Integer slaughterCount;
    /** 送宰均重 kg（当年送宰总重之和/送宰头数之和；分母 0 → null）。 */
    private BigDecimal avgSlaughterWeight;
    /** 屠宰出品率%（当年日接收重量之和/送宰总重之和×100；分母 0 → null）。 */
    private BigDecimal slaughterRate;
    /** 白条出品率%（当年日白条总重之和/送宰总重之和×100；分母 0 → null）。 */
    private BigDecimal barYieldRate;

    // ====== 年度分割指标统计 4 KPI（原型「年度分割指标统计」）======

    /** 分割白条数（当年日分割白条数之和；半只计 0.5、整只计 1，故为小数）。 */
    private BigDecimal cutBarCount;
    /** 分割总重（吨，当年日分割白条总重之和 / 1000）。 */
    private BigDecimal cutBarWeightTon;
    /** 分割品总重（吨，当年日分割产品总重之和 / 1000）。 */
    private BigDecimal cutProductWeightTon;
    /** 分割率%（当年日分割产品总重之和/分割白条总重之和×100；分母 0 → null）。 */
    private BigDecimal cutRate;

    // ====== 月度屠宰效能趋势（柱 + 3 折线，原型「月度屠宰效能趋势」）======

    /** 月份标签（yyyy-MM，按月份升序，仅所选年份），柱与折线共用 X 轴。 */
    private List<String> trendMonths = new ArrayList<>();
    /** 出栏头数序列（柱，主 Y 轴，与 trendMonths 等长）。 */
    private List<Integer> trendSlaughterCount = new ArrayList<>();
    /** 屠宰出品率%序列（折线，次 Y 轴，与 trendMonths 等长）。 */
    private List<BigDecimal> trendSlaughterRate = new ArrayList<>();
    /** 白条出品率%序列（折线，次 Y 轴）。 */
    private List<BigDecimal> trendBarYieldRate = new ArrayList<>();
    /** 分割出品率%序列（折线，次 Y 轴）。 */
    private List<BigDecimal> trendCutYieldRate = new ArrayList<>();

    // ====== 日猪肉处理数据统计矩阵（原型「日猪肉处理数据统计」）======

    /** 日期列（MM-dd，昨日 → 往前倒序，只到已发生日；与 matrixRows.dailyValues 等长）。 */
    private List<String> matrixDays = new ArrayList<>();
    /** 指标行（约 12 行；每行含中文指标名 + 逐日值 + 累计）。 */
    private List<MatrixRow> matrixRows = new ArrayList<>();

    /**
     * 日矩阵单指标行。值已在后端格式化成字符串（整数原样 / DECIMAL 2 位小数 / 空值空串），前端直接渲染。
     */
    @Data
    public static class MatrixRow implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 指标名（固定中文文案）。 */
        private String metric;
        /** 逐日值（与 matrixDays 顺序、长度一致）。 */
        private List<String> dailyValues = new ArrayList<>();
        /** 本月/区间累计（均值/率类留空串，不汇总）。 */
        private String total;
    }
}
