package org.dromara.djs.breed.farm.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 农场联系信息编辑入参（BRD-MD-002）。
 *
 * <p>只承载 {@code PUT /djs/breed/farm-info/contact} 端点允许修改的 3 个字段
 * （contact_name / contact_phone / address）。admin 不可编辑 farm_code / farm_name / farm_status，
 * 故不出现在本 BO 中（多一字段就多一道误改风险）。</p>
 *
 * @author djs
 * @since BRD-MD-002
 */
@Data
public class FarmContactBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 农场 ID（V1 固定 1001；不传或不等于 1001 由 Service 校验拒绝）。
     */
    @NotNull(message = "农场 ID 不能为空")
    private Long id;

    /**
     * 联系人。
     */
    @Size(max = 32, message = "联系人长度不能超过 {max} 个字符")
    private String contactName;

    /**
     * 联系电话。
     */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "请输入合法的手机号")
    @Size(max = 20, message = "联系电话长度不能超过 {max} 个字符")
    private String contactPhone;

    /**
     * 地址。
     */
    @Size(max = 255, message = "地址长度不能超过 {max} 个字符")
    private String address;

}
