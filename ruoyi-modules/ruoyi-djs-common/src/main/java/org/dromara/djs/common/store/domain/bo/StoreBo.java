package org.dromara.djs.common.store.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.common.store.domain.Store;

/**
 * 门店主数据入参 BO（SYS-MD-002）。
 *
 * <p>{@code storeCode} 不接受外部传入——新增由后端
 * {@link org.dromara.djs.common.encoder.IBizCodeGenerator}
 * 按 {@link org.dromara.djs.common.encoder.BizCodeType#STORE_CODE} 规则产出
 * （pattern {@code ST{seq4}}，例 {@code ST0001}）；编辑端点也不允许修改编码。</p>
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
     * 门店类型（V1 自由文本：{@code direct} / {@code franchise}）。
     */
    @Size(max = 16, message = "门店类型长度不能超过 {max} 个字符")
    private String storeType;

    /**
     * 经营状态（{@code 1}=合作中 / {@code 0}=已终止）。
     */
    @NotNull(message = "经营状态不能为空")
    private Integer businessStatus;

    /**
     * 门店地址。
     */
    @Size(max = 255, message = "地址长度不能超过 {max} 个字符")
    private String address;

    /**
     * 联系人。
     */
    @Size(max = 32, message = "联系人长度不能超过 {max} 个字符")
    private String contactName;

    /**
     * 联系电话。
     */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "请输入合法的手机号")
    private String contactPhone;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

}
