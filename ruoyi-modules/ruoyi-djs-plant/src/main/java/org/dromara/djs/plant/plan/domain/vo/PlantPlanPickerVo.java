package org.dromara.djs.plant.plan.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 种植计划 picker 轻量 VO（MP-PICKERS-001）。
 *
 * <p>给 mp {@code PlantPlanPicker} 用——农事录入 / 采摘选种植计划。只暴露 id + planNo +
 * cropName + plantStatus 4 字段，对应表 {@code t_plant_plant_plan}。</p>
 *
 * <p>注意：</p>
 * <ul>
 *   <li>{@code cropName} 由 controller 关联 {@code t_plant_crop_info.crop_name} 填充
 *       （plan 主表只存 crop_id）。</li>
 *   <li>{@code plantStatus} 用主表种植状态（字典 {@code djs_plant_plan_status}：
 *       pending / ongoing / completed / delayed）。原 ticket 草案写 harvestStatus，但
 *       采摘状态在明细表 {@code t_plant_plant_details} 是按地块维度，主表无此聚合字段，
 *       picker 选计划用主表 plantStatus 更合理。</li>
 *   <li>"active" 语义 = {@code plant_status IN ('pending','ongoing')}（与
 *       {@code PlantPlanMapper} 跨模块薄壳一致）。</li>
 * </ul>
 *
 * @author djs
 * @since MP-PICKERS-001
 */
@Data
public class PlantPlanPickerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 种植计划 ID（snowflake，Jackson 全局序列化为 string）。
     */
    private Long id;

    /**
     * 计划业务码（{@code PLAN-yyyy-NNN}）。
     */
    private String planNo;

    /**
     * 作物名称（由 crop_id 关联 {@code t_plant_crop_info.crop_name} 填充）。
     */
    private String cropName;

    /**
     * 计划状态（字典 {@code djs_plant_plan_status}：pending / ongoing / completed / delayed）。
     */
    private String plantStatus;

}
