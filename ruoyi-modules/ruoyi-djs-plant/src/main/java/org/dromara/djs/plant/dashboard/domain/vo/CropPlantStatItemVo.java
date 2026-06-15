package org.dromara.djs.plant.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 种植看板 - 实时种植物统计单行（区块 4，PLT-DASH-001）。
 *
 * <p>原型双轴图：bar = 该作物当前在种地块数（{@code COUNT(DISTINCT plot_id)}）、
 * line = 该作物预计产量合计 Kg（{@code SUM(expected_yield)}），均来自 {@code t_plant_plant_details}
 * 中未完成种植（{@code plant_status != 'completed'}）的明细，按 {@code crop_id} 分组 JOIN 作物名。</p>
 *
 * <p>无数据时返回空列表。</p>
 *
 * @author djs
 * @since PLT-DASH-001
 */
@Data
public class CropPlantStatItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 作物名称。
     */
    private String cropName;

    /**
     * 该作物当前在种地块数（{@code COUNT(DISTINCT plot_id)}，bar）。
     */
    private Integer plotCount;

    /**
     * 该作物预计产量合计 Kg（{@code SUM(expected_yield)}，line，无则 0）。
     */
    private BigDecimal expectedYield;

}
