package org.dromara.djs.warehouse.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 生产管理月度趋势 mapper 聚合原始行（内部用，不直接出参）。
 *
 * <p>承载「按月 GROUP BY」的一行：月份 + 主计数 + 两个附加聚合列（供 service 折算比率）。
 * service 据此补齐当年逐月 {@link ProductionTrendPointVo}（缺月补 0），故本 VO 不暴露给前端。</p>
 *
 * @author djs
 */
@Data
public class ProductionMonthRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 月份数字字符串（"1".."12"，来自 {@code CAST(MONTH(...) AS CHAR)}）。
     */
    private String name;

    /**
     * 主计数（送宰头数 / 处理批次数 / 发货单数）。
     */
    private BigDecimal value;

    /**
     * 附加聚合列 A（猪肉=当月到场总重；果蔬=当月处理总重；发货=当月发货总量）。
     */
    private BigDecimal extraA;

    /**
     * 附加聚合列 B（猪肉=当月燎毛总重，用于折算屠宰出品率；果蔬/发货=0）。
     */
    private BigDecimal extraB;

}
