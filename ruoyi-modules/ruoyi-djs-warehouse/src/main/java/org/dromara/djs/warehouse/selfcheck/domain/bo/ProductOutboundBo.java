package org.dromara.djs.warehouse.selfcheck.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 产品出库提交 BO（mp 库存盘点子系统 §3.C.5）。
 *
 * <p>字段名对齐 mp 契约 {@code stockCheck.ts} ProductOutboundBody。</p>
 *
 * @author djs
 * @since SELFCHECK
 */
@Data
public class ProductOutboundBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 库位 ID（snowflake string）。
     */
    @NotBlank(message = "库位不能为空")
    private String locationId;

    /**
     * 产品 ID（snowflake string）。
     */
    @NotBlank(message = "产品不能为空")
    private String productId;

    /**
     * 记录人 userId（snowflake string，可空，为空取当前登录人）。
     */
    private String operatorId;

    /**
     * 出库量（> 0）。
     */
    @NotNull(message = "出库量不能为空")
    @DecimalMin(value = "0.001", message = "出库量必须大于 0")
    private BigDecimal quantity;

    /**
     * 出库类型字典 {@code djs_inout_type}（出库侧，如 dept_pick 部门领用）。
     */
    private String inoutType;

    /**
     * 出库去向（如 矿山 / 门店）。
     */
    private String stockOutDest;

    /**
     * 出库日期 yyyy-MM-dd。
     */
    private String outboundDate;

}
