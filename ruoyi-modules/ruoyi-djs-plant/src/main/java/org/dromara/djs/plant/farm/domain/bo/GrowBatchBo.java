package org.dromara.djs.plant.farm.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 农事「作物 → 多地块批量录入」BO（FIX-PLT-MP-WORK-BATCH-001，#2）。
 *
 * <p>原型两段式：作物详情多选地块（radio + 仅看已选 + 全选）→ 批量录入弹窗（仅 日期 + 班组）。
 * 一次对所选 N 个地块下同一任务，单事务循环复用现有单条 buildRecord 插入逻辑。</p>
 *
 * <p>覆盖 6 类生长活动 + 退茬：</p>
 * <ul>
 *   <li>{@code water_fertilize} / {@code irrigation} / {@code weed} / {@code pest_control} / {@code pruning}：
 *       逐条 INSERT t_plant_farm_records，无额外副作用</li>
 *   <li>{@code rotation}（退茬）：每条 INSERT 后触发地块 plot_status=1（空闲）+ plant_details completed</li>
 * </ul>
 *
 * @author djs
 * @since FIX-PLT-MP-WORK-BATCH-001
 */
@Data
public class GrowBatchBo {

    /** 农事类型（生长段 6 类之一或 rotation；service 按此分支处理副作用）。 */
    @NotBlank(message = "{plant.farm.type.required}")
    @Size(max = 32)
    private String farmType;

    /** 批量目标地块（每项 plantId/plotId/cropId 三元组，从作物详情多选层带入）。 */
    @NotEmpty(message = "请至少选择一个地块")
    @Valid
    private List<PlotTarget> targets;

    /** 处理班组 id（批量统一）。 */
    @NotNull(message = "{plant.farm.team.required}")
    private Long farmBy;

    /** 农事日期（批量统一）。 */
    @NotNull(message = "{plant.farm.date.required}")
    private LocalDate farmDate;

    /** OSS 凭证图 ossIds，逗号分隔（批量统一，可空）。 */
    @Size(max = 500)
    private String proofOssIds;

    /** 备注（批量统一，可空）。 */
    @Size(max = 500)
    private String remark;

    /**
     * 单个批量目标地块（计划明细维度三元组）。
     */
    @Data
    public static class PlotTarget implements Serializable {

        /** 关联种植计划 id（{@code t_plant_plant_plan.id}）。 */
        @NotNull(message = "{plant.farm.plant_id.required}")
        private Long plantId;

        /** 地块 id（{@code t_plant_plot_info.id}）。 */
        @NotNull(message = "{plant.farm.plot.required}")
        private Long plotId;

        /** 作物 id（{@code t_plant_crop_info.id}）。 */
        @NotNull(message = "{plant.farm.crop.required}")
        private Long cropId;
    }
}
