package org.dromara.djs.warehouse.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 生产管理统计 — 单个指标项（label + 数值 + 可选单位/后缀）。
 *
 * <p>原型图 26「年度屠宰指标 / 年度分割指标」横排卡片：每张卡一个 label + 一个大数字。
 * 用通用 label/value 结构，前端按顺序渲染卡片（避免每 tab 写死字段）。</p>
 *
 * <p>{@code value} 统一 BigDecimal；比率类指标（屠宰出品率等）后端已乘 100 折算为百分数值
 * （如 80.21 表示 80.21%），{@code suffix} 给 "%" / "kg" / "头" 等供前端拼接，避免前端再做单位推断。</p>
 *
 * @author djs
 */
@Data
public class ProductionStatItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 指标中文标签（如「送宰头数」「屠宰出品率」）。
     */
    private String label;

    /**
     * 指标数值（计数为整数语义但统一 BigDecimal；比率已折算为百分数值，无数据为 0）。
     */
    private BigDecimal value;

    /**
     * 单位/后缀（如 "%"、"kg"、"头"、"吨"；无单位为空串）。
     */
    private String suffix;

    public ProductionStatItemVo() {
    }

    public ProductionStatItemVo(String label, BigDecimal value, String suffix) {
        this.label = label;
        this.value = value == null ? BigDecimal.ZERO : value;
        this.suffix = suffix == null ? "" : suffix;
    }

}
