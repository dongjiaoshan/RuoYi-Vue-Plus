package org.dromara.djs.warehouse.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 生产管理「月度效能趋势」单点。
 *
 * <p>原型图 26 猪肉 tab「月度屠宰效能趋势」是柱+折线复合图：柱 = 当月出栏头数（{@code barValue}），
 * 折线 = 当月屠宰出品率 / 白条出品率 / 分割出品率（3 条 rate）。</p>
 *
 * <p>mp 当前图表组件（TrendChart）仅支持单折线，前端 V1 以 {@code barValue}（月度处理头数/单数）渲染主趋势线，
 * 3 条 rate 字段保留供后续复合图升级，不丢数据。果蔬/发货 tab 复用本结构：{@code barValue} 为当月处理批次数/发货单数，
 * rate 字段按 tab 语义填（无对应概念则为 0）。</p>
 *
 * <p>所有 rate 已折算为百分数值（如 80.21 表示 80.21%），无数据均为 0。</p>
 *
 * @author djs
 */
@Data
public class ProductionTrendPointVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * X 轴 label（月份，如「1月」）。
     */
    private String label;

    /**
     * 柱值：当月主处理量（猪肉=出栏/送宰头数；果蔬=处理批次数；发货=发货单数）。
     */
    private BigDecimal barValue;

    /**
     * 折线①：当月屠宰出品率（百分数值，仅猪肉 tab 有意义，其余为 0）。
     */
    private BigDecimal slaughterRate;

    /**
     * 折线②：当月白条出品率（百分数值，仅猪肉 tab 有意义，其余为 0）。
     */
    private BigDecimal barRate;

    /**
     * 折线③：当月分割出品率（百分数值，仅猪肉 tab 有意义，其余为 0）。
     */
    private BigDecimal cutRate;

    public ProductionTrendPointVo() {
    }

    public ProductionTrendPointVo(String label, BigDecimal barValue,
                                  BigDecimal slaughterRate, BigDecimal barRate, BigDecimal cutRate) {
        this.label = label;
        this.barValue = nz(barValue);
        this.slaughterRate = nz(slaughterRate);
        this.barRate = nz(barRate);
        this.cutRate = nz(cutRate);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

}
