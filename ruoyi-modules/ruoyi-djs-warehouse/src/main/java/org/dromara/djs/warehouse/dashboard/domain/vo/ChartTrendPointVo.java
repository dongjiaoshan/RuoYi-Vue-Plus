package org.dromara.djs.warehouse.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 看板折线图单点（日期 + 当日聚合值）。
 *
 * <p>用于生产趋势、损耗趋势 2 张时序折线图。{@code date} 为 {@code yyyy-MM-dd}，
 * {@code value} 为该日聚合值（求和）。后端按近 N 日补齐每天一个点（无数据补 0），
 * 前端直接渲染连续折线。</p>
 *
 * @author djs
 */
@Data
public class ChartTrendPointVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日期（yyyy-MM-dd）。
     */
    private String date;

    /**
     * 当日聚合值（求和；无数据为 0）。
     */
    private BigDecimal value;

    public ChartTrendPointVo() {
    }

    public ChartTrendPointVo(String date, BigDecimal value) {
        this.date = date;
        this.value = value == null ? BigDecimal.ZERO : value;
    }

}
