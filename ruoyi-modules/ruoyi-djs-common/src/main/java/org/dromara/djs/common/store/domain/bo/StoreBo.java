package org.dromara.djs.common.store.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.common.store.domain.Store;

import java.time.LocalDate;

/**
 * 门店主数据入参 BO（SYS-MD-002 + SYS-MD-FIX-002）。
 *
 * <p>{@code storeCode} 不接受外部传入——新增由后端
 * {@link org.dromara.djs.common.encoder.IBizCodeGenerator}
 * 按 {@link org.dromara.djs.common.encoder.BizCodeType#STORE_CODE} 规则产出
 * （pattern {@code ST{seq4}}，例 {@code ST0001}）；编辑端点也不允许修改编码。</p>
 *
 * <p>{@code managerUserId} 不在编辑表单字段中，必须走独立端点
 * {@code PUT /djs/common/store/{id}/manager}（防越权）。</p>
 *
 * @author djs
 * @since SYS-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = Store.class, reverseConvertGenerate = false)
public class StoreBo extends BaseEntity {

    /**
     * 门店 ID（编辑时必填）。
     */
    private Long id;

    /**
     * 门店名称。
     */
    @NotBlank(message = "门店名称不能为空")
    @Size(max = 64, message = "门店名称长度不能超过 {max} 个字符")
    private String storeName;

    /**
     * 门店简称。
     */
    @Size(max = 64, message = "门店简称长度不能超过 {max} 个字符")
    private String shortName;

    /**
     * 开业日期。
     */
    private LocalDate openDate;

    /**
     * 门店类型（字典 djs_store_type：direct / franchise）。
     */
    @Size(max = 16, message = "门店类型长度不能超过 {max} 个字符")
    private String storeType;

    /**
     * 合作状态（字典 djs_store_status：0=合作中 / 1=已终止 / 2=装修中）。
     */
    @NotBlank(message = "合作状态不能为空")
    @Size(max = 16, message = "合作状态长度不能超过 {max} 个字符")
    private String businessStatus;

    /**
     * 门店地址。
     */
    @Size(max = 255, message = "地址长度不能超过 {max} 个字符")
    private String address;

    /**
     * 店长姓名（文本）。
     */
    @Size(max = 32, message = "店长姓名长度不能超过 {max} 个字符")
    private String managerName;

    /**
     * 店长电话。
     */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "请输入合法的手机号")
    private String managerPhone;

    /**
     * 收银系统 ID。
     */
    @Size(max = 64, message = "收银系统 ID 长度不能超过 {max} 个字符")
    private String posSystemId;

    /**
     * 生产标识码（门店级唯一，用于门店打包生产编码前缀）。
     *
     * <p>新增必填（row130）；编辑不强制——存量未配置的老门店仍可编辑其他字段。</p>
     */
    @NotBlank(message = "生产标识码不能为空", groups = OnCreate.class)
    @Size(max = 32, message = "生产标识码长度不能超过 {max} 个字符")
    private String productionMarkCode;

    /**
     * 门店图片（OSS oss_id）。
     */
    private Long imageOssId;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

    /** 新增校验组（编辑不校验的仅新增约束挂此组）。 */
    public interface OnCreate {
    }

}
