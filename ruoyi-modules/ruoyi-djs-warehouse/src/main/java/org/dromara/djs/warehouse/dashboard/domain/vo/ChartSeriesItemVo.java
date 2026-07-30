package org.dromara.djs.warehouse.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 看板饼图 / 环形图通用 series item（name + value）。
 *
 * <p>用于需求饼、退货环、盘点饼、异常库位环 4 张占比类图。{@code name} 为分类标签，
 * {@code value} 为该分类的聚合值（计数或求和）。</p>
 *
 * @author djs
 */
@Data
public class ChartSeriesItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分类标签（业态 / 退货方向 / 盘点结果 / 正常异常等）。
     */
    private String name;

    /**
     * 聚合值（计数或求和；null-safe，无数据返 0）。
     */
    private BigDecimal value;

    /**
     * 产品单位（product_unit，供前端 KG / 非 KG 维度过滤；'kg' 为 KG，其余 / 空为非 KG）。
     * 仅按产品名聚合的需求 / 退货分布 series 填充，其他分类类 series 为 null。
     */
    private String unit;

    public ChartSeriesItemVo() {
    }

    public ChartSeriesItemVo(String name, BigDecimal value) {
        this.name = name;
        this.value = value == null ? BigDecimal.ZERO : value;
    }

}
