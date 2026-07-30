package org.dromara.djs.breed.event.slaughter.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出栏事件录入入参（BRD-EVENT-004 SLAUGHTER）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
public class SlaughterBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 母猪 ID（与 earNo 二选一；admin 端可直传 id，mp 端建议传 earNo）。 */
    private Long pigId;

    /** 猪只耳号简版（mp 端工人输入；与 pigId 二选一，service 入口按 earNo 查 pig.id）。 */
    private String earNo;

    @NotNull(message = "{slaughter.date.required}")
    private LocalDateTime marketingDate;

    /** 出栏重量 kg（> 0）。 */
    @NotNull(message = "{slaughter.weight.required}")
    @DecimalMin(value = "0.01", message = "{slaughter.weight.positive}")
    private BigDecimal outWeight;

    /** 出栏去向（字典 out_house_dest）。 */
    @NotBlank(message = "{slaughter.dest.required}")
    @Size(max = 32, message = "{slaughter.dest.size}")
    private String outDest;

    /** 目标门店 ID（如适用）。 */
    private Long storeId;

    /** 多角度照片（强制至少 1 张）。 */
    @NotBlank(message = "{slaughter.photo.required}")
    @Size(max = 1024, message = "{slaughter.photo.size}")
    private String ossIds;

    /** 出栏操作员 userId（EmployeePicker 所选；可空，mp 端工人选员工，与 operator_id 登录态审计列并存）。 */
    @Size(max = 64, message = "{slaughter.operator.size}")
    private String operator;

    @Size(max = 500, message = "{slaughter.remark.size}")
    private String remark;
}
