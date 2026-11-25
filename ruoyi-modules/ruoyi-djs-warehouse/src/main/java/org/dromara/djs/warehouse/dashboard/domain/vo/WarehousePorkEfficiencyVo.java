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
 * <p>年度送宰头数与均重使用出栏源，白条出品率与分割率读年度接口的月表汇总；
 * 月趋势的分割率读月表，其余趋势与日矩阵共用日表。</p>
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

    /** 送宰头数（按当年出栏或送宰日期累计）。 */
    private Integer slaughterCount;
    /** 送宰均重 kg（当年出栏/送宰源总重 ÷ 同一批猪头数；无头数 → null）。 */
    private BigDecimal avgSlaughterWeight;
    /** 屠宰出品率%（当年非NULL日屠宰出品率之和/该字段有值日数；真实0计入，无值 → null）。 */
    private BigDecimal slaughterRate;
    /** 白条出品率%（当年可比日均值，实录 0% 计入，无可比日时为 0）。 */
    private BigDecimal barYieldRate;

    // ====== 年度分割指标统计 4 KPI（原型「年度分割指标统计」）======

    /** 分割扇数（当年按领用日累计；半扇 1、整只 2）。 */
    private BigDecimal cutBarCount;
    /** 分割总重（吨，当年日分割白条总重之和 / 1000）。 */
    private BigDecimal cutBarWeightTon;
    /** 分割品总重（吨，当年日分割产品总重之和 / 1000）。 */
    private BigDecimal cutProductWeightTon;
    /** 分割率%（按领用年同批产出重/领用重×100，分母为 0 时返回 0）。 */
    private BigDecimal cutRate;

    // ====== 月度屠宰效能趋势（柱 + 3 折线，原型「月度屠宰效能趋势」）======

    /** 月份标签（yyyy-MM，按月份升序，仅所选年份），柱与折线共用 X 轴。 */
    private List<String> trendMonths = new ArrayList<>();
    /** 当月接收头数序列（柱，主 Y 轴，与 trendMonths 等长）。 */
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
     * 日矩阵单指标行。值已在后端格式化成字符串，缺源出栏重时白条出品率为空串。
     */
    @Data
    public static class MatrixRow implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 指标名（固定中文文案）。 */
        private String metric;
        /** 逐日值（与 matrixDays 顺序、长度一致）。 */
        private List<String> dailyValues = new ArrayList<>();
        /** 本月/区间累计。 */
        private String total;
    }
}
