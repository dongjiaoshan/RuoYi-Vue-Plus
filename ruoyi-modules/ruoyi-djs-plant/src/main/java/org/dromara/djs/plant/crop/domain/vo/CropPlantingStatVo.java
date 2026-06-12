package org.dromara.djs.plant.crop.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 作物派生统计聚合行（FIX-PLT-AD-INFO-LIST-001）。
 *
 * <p>按 {@code crop_id} 聚合 {@code t_plant_plant_details}：
 * 历史种植次数 COUNT / 平均亩产 AVG(average_yield) / 最大亩产 MAX(average_yield)。
 * service 层批量 enrich 回填 {@link CropInfoVo}，避免列表 N+1。</p>
 *
 * @author djs
 * @since FIX-PLT-AD-INFO-LIST-001
 */
@Data
public class CropPlantingStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 作物 ID。 */
    private Long cropId;

    /** 历史种植次数（明细行数）。 */
    private Long historyPlantCount;

    /** 平均亩产 kg/亩。 */
    private BigDecimal avgYield;

    /** 最大亩产 kg/亩。 */
    private BigDecimal maxYield;
}
