package org.dromara.djs.plant.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 种植看板 - 今日农事按类型分桶单行（PLT-DASH-001）。
 *
 * <p>来源：{@code t_plant_farm_records} WHERE {@code farm_date = CURDATE()}
 * GROUP BY {@code farm_type}，每个农事类型一行计数。农事类型中文 label 由前端
 * 走 {@code djs_farm_work_type} 字典映射，be 只返原始 {@code farmType} code + count。</p>
 *
 * @author djs
 * @since PLT-DASH-001
 */
@Data
public class FarmWorkCountVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 农事类型 code（字典 {@code djs_farm_work_type}：tillage_break / fertilize / harvest_activity 等）。
     */
    private String farmType;

    /**
     * 该类型今日农事条数。
     */
    private Integer count;

}
