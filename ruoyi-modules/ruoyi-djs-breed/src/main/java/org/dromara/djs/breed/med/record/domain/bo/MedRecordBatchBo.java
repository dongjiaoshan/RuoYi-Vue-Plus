package org.dromara.djs.breed.med.record.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量用药入参 BO（BRD-MED-003）。
 *
 * <p>语义：N 头猪共享同一批次 / 同一药品 / 同一剂量（每头 dosage 单独一份），
 * service 端拆为 1 条 master + N 条 detail，单事务包。N ≤ 200，
 * 超过返 {@code medicine.batch.too_large}。</p>
 *
 * @author djs
 * @since BRD-MED-003
 */
@Data
public class MedRecordBatchBo {

    /**
     * 用药日期（业务日期，必填）。
     */
    @NotNull(message = "用药日期不能为空")
    private LocalDateTime useDate;

    /**
     * 猪只 ID 列表（必填，每头 ≥ 1 ≤ 200）。
     */
    @NotEmpty(message = "猪只列表不能为空")
    @Size(max = 200, message = "批量用药一次最多 {max} 头")
    private List<Long> pigIds;

    /**
     * 用药类型（djs_medicine_use_type，必填，共享）。
     */
    @NotNull(message = "用药类型不能为空")
    @Size(max = 32)
    private String medicineType;

    /**
     * 疫苗类型（djs_vaccine_type，仅免疫类用药时填写，可空，共享）。
     */
    @Size(max = 32, message = "疫苗类型长度不能超过 32")
    private String vaccineType;

    /**
     * 用药原因（可选，共享）。
     */
    @Size(max = 32)
    private String medicineReason;

    /**
     * 用药方式（djs_medicine_way，必填，共享）。
     */
    @NotNull(message = "用药方式不能为空")
    @Size(max = 32)
    private String medicineWay;

    /**
     * 药品 ID（必填，共享）。
     */
    @NotNull(message = "药品 ID 不能为空")
    private Long medicineId;

    /**
     * 批次 ID（必填，共享）。
     */
    @NotNull(message = "批次 ID 不能为空")
    private Long batchId;

    /**
     * 领用 ID（可选，共享）。
     */
    private Long usageId;

    /**
     * 用药 schedule ID（可选，共享）。
     */
    private Long scheduleId;

    /**
     * 每头剂量（必填，> 0；总扣减 = dosage × pigIds.size）。
     */
    @NotNull(message = "用药剂量不能为空")
    @DecimalMin(value = "0.001", message = "用药剂量必须大于 0")
    private BigDecimal medicineDosage;

    /**
     * 备注（共享）。
     */
    @Size(max = 500)
    private String remark;

    /**
     * 用药人 user_id（选填；mp 默认当前登录人可改选。空 → 后端回落 LoginHelper.getUserId）。
     */
    private Long operatorId;

    /**
     * 用药人姓名快照（前端 EmployeePicker 选中一并传；空 → 后端取登录人昵称）。
     */
    private String operatorName;

}
