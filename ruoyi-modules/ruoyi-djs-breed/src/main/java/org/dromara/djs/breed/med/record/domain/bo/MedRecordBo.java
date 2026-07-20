package org.dromara.djs.breed.med.record.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.breed.med.record.domain.MedRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单只用药入参 BO（BRD-MED-003）。
 *
 * <p>service 端校验顺序：</p>
 * <ol>
 *   <li>pig 存在 + 非终态 + earNo 一致；</li>
 *   <li>batch 存在 + 3 天内已领 + 余量 ≥ dosage；</li>
 *   <li>扣 quantity（含 stock_qty 上界）；</li>
 *   <li>INSERT 记录，operator_id 来自 LoginHelper。</li>
 * </ol>
 *
 * @author djs
 * @since BRD-MED-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = MedRecord.class, reverseConvertGenerate = false)
public class MedRecordBo extends BaseEntity {

    /**
     * 主键（编辑场景占位；本 ticket 不支持 edit）。
     */
    private Long id;

    /**
     * 用药日期（业务日期，必填）。
     */
    @NotNull(message = "用药日期不能为空")
    private LocalDateTime useDate;

    /**
     * 猪只 ID（必填）。
     */
    @NotNull(message = "猪只 ID 不能为空")
    private Long pigId;

    /**
     * 用药类型（djs_medicine_use_type，必填）。
     */
    @NotNull(message = "用药类型不能为空")
    @Size(max = 32)
    private String medicineType;

    /**
     * 用药原因（djs_medicine_reason，可选；治疗类强烈建议填）。
     */
    @Size(max = 32)
    private String medicineReason;

    /**
     * 用药方式（djs_medicine_way，必填）。
     */
    @NotNull(message = "用药方式不能为空")
    @Size(max = 32)
    private String medicineWay;

    /**
     * 药品 ID（必填）。药品即仓库商品，废弃批次；库存按本字段扣减。
     */
    @NotNull(message = "药品 ID 不能为空")
    private Long medicineId;

    /**
     * 批次 ID（药品废弃批次后不再必填/不再传；保留字段兼容旧数据 record.batch_id）。
     */
    private Long batchId;

    /**
     * 领用记录 ID（可选，追溯 3 天领用源头；service 端按 batchId + 3 天窗口反查兜底）。
     */
    private Long usageId;

    /**
     * 用药 schedule ID（可选，V1 仅记录不强校验归属）。
     */
    private Long scheduleId;

    /**
     * 用药剂量（必填，> 0）。
     */
    @NotNull(message = "用药剂量不能为空")
    @DecimalMin(value = "0.001", message = "用药剂量必须大于 0")
    private BigDecimal medicineDosage;

    /**
     * 剂量单位（记录用，g/mg/ml/L/kg/片 等，选填，不参与库存扣减）。
     */
    @Size(max = 32)
    private String dosageUnit;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

    /**
     * 用药人 user_id（选填；mp 端默认当前登录人，可改选其他员工。空 → 后端回落 LoginHelper.getUserId）。
     */
    private Long operatorId;

    /**
     * 用药人姓名快照（前端 EmployeePicker 选中时一并传，避免后端跨模块查 sys_user；空 → 后端取登录人昵称）。
     */
    private String operatorName;

}
