package org.dromara.djs.plant.farm.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 灾害「作物 → 多地块批量录入」BO（FIX-PLT-MP-WORK-BATCH-001 灾害批量，#2）。
 *
 * <p>原型两段式：作物详情多选地块（每块展示标准/损失产量 + 历史灾种）→ 批量弹窗
 * （灾害记录日期 + 灾害类型▼ + 灾害损失率% + 记录班组）。一次对所选 N 个地块下同一灾害，
 * 单事务循环复用单条灾害插入逻辑（含 plant_details.loss_yield 累加）。</p>
 *
 * <p>与 {@link GrowBatchBo} 区别：灾害多 {@code disasterType} / {@code lossRate}（批量统一）+
 * 每地块独立 {@code lossYield}（按各自标准产量 × 损失率算出，前端带入）。</p>
 *
 * @author djs
 * @since FIX-PLT-MP-WORK-BATCH-001
 */
@Data
public class DisasterBatchBo {

    /** 批量目标地块（每项三元组 + 该地块损失产量）。 */
    @NotEmpty(message = "请至少选择一个地块")
    @Valid
    private List<PlotTarget> targets;

    /** 处理班组 id（批量统一）。 */
    @NotNull(message = "{plant.farm.team.required}")
    private Long farmBy;

    /** 处理班组全集（G1-TEAMS-MULTISELECT，row37）。非空时写中间表，旧单列 farmBy 取第一个；为 null 时退化为单值 farmBy。 */
    private java.util.List<Long> farmByIds;

    /** 灾害记录日期（批量统一）。 */
    @NotNull(message = "{plant.farm.date.required}")
    private LocalDate farmDate;

    /** 灾害类型（字典 djs_disaster_type，批量统一）。 */
    @NotNull(message = "{plant.farm.disaster_type.required}")
    @Size(max = 32)
    private String disasterType;

    /** 损失率 0-100（批量统一）。 */
    @NotNull(message = "{plant.farm.loss_rate.required}")
    @DecimalMin(value = "0", message = "{plant.farm.loss_rate.range}")
    @DecimalMax(value = "100", message = "{plant.farm.loss_rate.range}")
    private BigDecimal lossRate;

    /** OSS 凭证图 ossIds，逗号分隔（批量统一，可空）。 */
    @Size(max = 500)
    private String proofOssIds;

    /** 备注（批量统一，可空）。 */
    @Size(max = 500)
    private String remark;

    /** 单个批量目标地块（计划明细维度三元组 + 该地块损失产量）。 */
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

        /** 该地块损失产量 kg（前端按标准产量 × lossRate 算出；累加进对应 plant_details.loss_yield）。 */
        @PositiveOrZero(message = "{plant.farm.loss_yield.positive}")
        private BigDecimal lossYield;
    }
}
