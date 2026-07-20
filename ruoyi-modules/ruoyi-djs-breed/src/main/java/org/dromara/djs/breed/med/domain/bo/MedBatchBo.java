package org.dromara.djs.breed.med.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.breed.med.domain.MedBatch;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 药品批次入参 BO（BRD-MED-001）。
 *
 * @author djs
 * @since BRD-MED-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = MedBatch.class, reverseConvertGenerate = false)
public class MedBatchBo extends BaseEntity {

    /**
     * 批次 ID（编辑时必填）。
     */
    private Long id;

    /**
     * 药品 ID（编辑时禁止修改，仍透传方便 form 一并 patch）。
     */
    @NotNull(message = "药品 ID 不能为空")
    private Long medicineId;

    /**
     * 批次编码。
     */
    @NotBlank(message = "批次编码不能为空")
    @Size(max = 64, message = "批次编码长度不能超过 {max} 个字符")
    private String batchNo;

    /**
     * 生产日期。
     */
    private LocalDate productionDate;

    /**
     * 过期日期。
     */
    private LocalDate expiryDate;

    /**
     * 批次剩余库存。
     */
    @PositiveOrZero(message = "库存不能为负数")
    private BigDecimal quantity;

    /**
     * 进货单价。
     */
    @PositiveOrZero(message = "单价不能为负数")
    private BigDecimal unitPrice;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

}
