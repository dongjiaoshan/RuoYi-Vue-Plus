package org.dromara.djs.common.supplier.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.common.supplier.domain.Supplier;

import java.time.LocalDate;

/**
 * 供应商主数据入参 BO（SYS-MD-003 + SYS-MD-FIX-002）。
 *
 * <p>{@code supplierCode} 不接受外部传入 —— 新增由后端编码生成器产出；编辑时也不允许改编码。
 * {@code dealCount} / {@code purchaseQty} 也不接受外部传入（聚合冗余字段，由下游业务事件回填）。</p>
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
     * 营业执照编号。
     */
    @Size(max = 64, message = "营业执照编号长度不能超过 {max} 个字符")
    private String licenseNo;

    /**
     * 营业执照图片（OSS oss_id）。
     */
    private Long licenseImageOssId;

    /**
     * 经营许可证编号。
     */
    @Size(max = 64, message = "经营许可证编号长度不能超过 {max} 个字符")
    private String businessLicenseNo;

    /**
     * 合作开始日期。
     */
    private LocalDate cooperationStartDate;

    /**
     * 供应商类型（字典 djs_supplier_type）。
     */
    @NotBlank(message = "供应商类型不能为空")
    @Size(max = 32, message = "供应商类型取值非法")
    private String supplierType;

    /**
     * 联系负责人姓名。
     */
    @Size(max = 32, message = "负责人姓名长度不能超过 {max} 个字符")
    private String liaisonName;

    /**
     * 负责人电话（座机 / 手机均允许，仅做长度限制）。
     */
    @Size(max = 20, message = "负责人电话长度不能超过 {max} 个字符")
    @Pattern(regexp = "^$|^[0-9+\\-\\s]{6,20}$", message = "请输入合法的电话号码")
    private String liaisonPhone;

    /**
     * 地址。
     */
    @Size(max = 255, message = "地址长度不能超过 {max} 个字符")
    private String address;

    /**
     * 合作状态（字典 djs_supplier_status：0=合作中 / 1=已终止）。
     */
    @NotBlank(message = "合作状态不能为空")
    @Size(max = 16, message = "合作状态长度不能超过 {max} 个字符")
    private String businessStatus;

    /**
     * 结算方式（字典 djs_settle_type：cash / monthly / quarterly）。
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
