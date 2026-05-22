package org.dromara.djs.breed.med.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.breed.med.domain.Medicine;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 药品库入参 BO（BRD-MED-001）。
 *
 * <p>{@code medicineCode} 由前端填入（药品编码客户希望可自定义，与门店 / 供应商等系统编码生成场景不同；
 * 编辑端点不允许修改避免破坏下游引用）。</p>
 *
 * @author djs
 * @since BRD-MED-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = Medicine.class, reverseConvertGenerate = false)
public class MedicineBo extends BaseEntity {

    /**
     * 药品 ID（编辑时必填）。
     */
    private Long id;

    /**
     * 药品编码（新增时必填，编辑时忽略；UNIQUE 约束在 DB 层兜底）。
     */
    @NotBlank(message = "药品编码不能为空", groups = OnCreate.class)
    @Size(max = 32, message = "药品编码长度不能超过 {max} 个字符")
    private String medicineCode;

    /**
     * 药品名称。
     */
    @NotBlank(message = "药品名称不能为空")
    @Size(max = 128, message = "药品名称长度不能超过 {max} 个字符")
    private String medicineName;

    /**
     * 药品类型（字典 {@code djs_med_type}）。
     */
    @NotBlank(message = "药品类型不能为空")
    @Size(max = 16, message = "药品类型长度不能超过 {max} 个字符")
    private String medicineType;

    /**
     * 供应商 ID（关联 {@code t_md_supplier.id}，admin 表单走 {@code /djs/common/supplier/list} 选择）。
     */
    private Long supplierId;

    /**
     * 批准文号。
     */
    @Size(max = 64, message = "批准文号长度不能超过 {max} 个字符")
    private String approvalNo;

    /**
     * 批号。
     */
    @Size(max = 64, message = "批号长度不能超过 {max} 个字符")
    private String batchNo;

    /**
     * 过期日期。
     */
    private LocalDate expireDate;

    /**
     * 休药期（天）。
     */
    @PositiveOrZero(message = "休药期不能为负数")
    private Integer withdrawDays;

    /**
     * 计量单位。
     */
    @Size(max = 16, message = "单位长度不能超过 {max} 个字符")
    private String unit;

    /**
     * 当前库存。
     */
    @PositiveOrZero(message = "当前库存不能为负数")
    private BigDecimal currentStock;

    /**
     * 规格。
     */
    @Size(max = 128, message = "规格长度不能超过 {max} 个字符")
    private String spec;

    /**
     * 生产厂家。
     */
    @Size(max = 128, message = "生产厂家长度不能超过 {max} 个字符")
    private String manufacturer;

    /**
     * 储存条件。
     */
    @Size(max = 200, message = "储存条件长度不能超过 {max} 个字符")
    private String storageCondition;

    /**
     * 药品状态（1 启用 / 0 停用）。
     */
    private Integer medStatus;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

    /**
     * 新增校验组（仅校验创建时必填字段，例 {@code medicineCode} 编辑时复用旧值不强制传）。
     */
    public interface OnCreate {
    }

}
