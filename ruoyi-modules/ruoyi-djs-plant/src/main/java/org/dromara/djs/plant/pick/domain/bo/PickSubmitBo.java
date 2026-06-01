package org.dromara.djs.plant.pick.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * mp 工人采收录入入参 BO（PLT-PICK-001）。
 *
 * <p>工人从待采列表 / 详情进入，{@code detailId} 由路由 query 带入（不手输 ID）。提交后
 * 累加 {@code t_plant_plant_details.actual_yield} + 回填 {@code begin_harvestdate} + 流转
 * {@code harvest_status}；{@code finish=true} 时额外置 {@code end_actualdate=NOW} +
 * {@code harvest_status='completed'}，并 INSERT 一行 {@code t_plant_farm_records}
 * （{@code farm_type='harvest_activity'}）。</p>
 *
 * @author djs
 * @since PLT-PICK-001
 */
@Data
public class PickSubmitBo {

    /** 采摘明细 id（{@code t_plant_plant_details.id}）。 */
    @NotNull(message = "采摘明细 id 必填")
    private Long detailId;

    /** 本次采收重量 kg（必填 > 0，累加进 actual_yield）。 */
    @NotNull(message = "采收重量必填")
    @DecimalMin(value = "0.001", message = "采收重量必须大于 0")
    private BigDecimal weight;

    /** 采收日期（必填）。 */
    @NotNull(message = "采收日期必填")
    private LocalDate harvestDate;

    /** 是否完成采收（true 置 completed + end_actualdate；默认 false 继续采）。 */
    private Boolean finish = false;

    /** 凭证图 OSS id（可选；bizType=plant_farm_proof）。 */
    private List<Long> proofOssIds;

    /** 备注（可选）。 */
    @Size(max = 500, message = "备注不超过 500 字")
    private String remark;
}
