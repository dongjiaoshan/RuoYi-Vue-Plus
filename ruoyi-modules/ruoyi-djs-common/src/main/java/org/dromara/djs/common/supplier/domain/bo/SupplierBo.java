package org.dromara.djs.common.supplier.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.common.supplier.domain.Supplier;

/**
 * 供应商主数据入参 BO（SYS-MD-003）。
 *
 * <p>{@code supplierCode} 不接受外部传入 —— 新增由后端编码生成器产出；编辑时也不允许改编码。</p>
 *
 * @author djs
 * @since SYS-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = Supplier.class, reverseConvertGenerate = false)
public class SupplierBo extends BaseEntity {

    /**
     * 供应商 ID（编辑时必填）。
     */
    private Long id;

    /**
     * 供应商名称。
     */
    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 128, message = "供应商名称长度不能超过 {max} 个字符")
    private String supplierName;

    /**
     * 供应商类型（字典 djs_supplier_type）。
     */
    @NotBlank(message = "供应商类型不能为空")
    @Size(max = 32, message = "供应商类型取值非法")
    private String supplierType;

    /**
     * 联系人。
     */
    @Size(max = 32, message = "联系人长度不能超过 {max} 个字符")
    private String contactName;

    /**
     * 联系电话（座机 / 手机均允许，仅做长度限制）。
     */
    @Size(max = 20, message = "联系电话长度不能超过 {max} 个字符")
    @Pattern(regexp = "^$|^[0-9+\\-\\s]{6,20}$", message = "请输入合法的联系电话")
    private String contactPhone;

    /**
     * 地址。
     */
    @Size(max = 255, message = "地址长度不能超过 {max} 个字符")
    private String address;

    /**
     * 业务状态（1 启用 / 0 停用）。
     */
    @NotNull(message = "业务状态不能为空")
    private Integer businessStatus;

    /**
     * 结算方式。
     */
    @Size(max = 16, message = "结算方式长度不能超过 {max} 个字符")
    private String settleType;

    /**
     * 银行账户。
     */
    @Size(max = 64, message = "银行账户长度不能超过 {max} 个字符")
    private String bankAccount;

    /**
     * 开户行。
     */
    @Size(max = 64, message = "开户行长度不能超过 {max} 个字符")
    private String bankName;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

}
