package org.dromara.djs.plant.farm.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 生长阶段农事录入 BO（PLT-WORK-001）。
 *
 * <p>覆盖 6 类：{@code water_fertilize} / {@code irrigation} / {@code weed} / {@code pest_control} /
 * {@code pruning} / {@code harvest_activity}。退茬（rotation）走 {@link RotationRecordBo} 独立路径，
 * 因为需要触发地块状态回归 + 计划明细 completed。移栽 / 灾害走独立 BO。</p>
 *
 * @author djs
 * @since PLT-WORK-001
 */
@Data
public class GrowRecordBo {

    /** 农事类型（djs_farm_work_type 生长段，不含 rotation / transplant / disaster）。 */
    @NotBlank(message = "{plant.farm.type.required}")
    @Size(max = 32)
    private String farmType;

    /** 关联种植计划（生长阶段必有计划）。 */
    @NotNull(message = "{plant.farm.plant_id.required}")
    private Long plantId;

    /** 地块 ID。 */
    @NotNull(message = "{plant.farm.plot.required}")
    private Long plotId;

    /** 作物 ID（生长阶段必填，冗余写入 crop_name）。 */
    @NotNull(message = "{plant.farm.crop.required}")
    private Long cropId;

    /** 处理班组 ID。 */
    @NotNull(message = "{plant.farm.team.required}")
    private Long farmBy;

    /** 操作人 sys_user.user_id（可空；采收链路已改按班组记录走 farm_by，本字段保留兼容其他农事/历史数据）。 */
    private Long operatorUserId;

    /** 农事日期。 */
    @NotNull(message = "{plant.farm.date.required}")
    private LocalDate farmDate;

    @Size(max = 500)
    private String proofOssIds;

    @Size(max = 500)
    private String remark;
}
