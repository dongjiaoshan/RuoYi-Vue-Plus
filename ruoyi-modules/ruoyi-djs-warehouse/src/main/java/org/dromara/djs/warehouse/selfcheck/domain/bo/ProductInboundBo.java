package org.dromara.djs.warehouse.selfcheck.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 产品入库提交 BO（mp 库存盘点子系统 §3.C.4）。
 *
 * <p>字段名对齐 mp 契约 {@code stockCheck.ts} ProductInboundBody。snowflake ID 跨层 string，
 * service 内显式 parse 为 Long（防 JS 大数截断）。</p>
 *
 * @author djs
 * @since SELFCHECK
 */
@Data
public class ProductInboundBo implements Serializable {

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
     * 负责人员 userId（snowflake string，可空，为空取当前登录人）。
     */
    private String operatorId;

    /**
     * 入库量（> 0）。
     */
    @NotNull(message = "入库量不能为空")
    @DecimalMin(value = "0.001", message = "入库量必须大于 0")
    private BigDecimal quantity;

    /**
     * 入库类型字典 {@code djs_inout_type}（入库侧，如 purchase 采购入库）。
     */
    private String inoutType;

    /**
     * 供应商业务码（按 supplier_code 查 t_md_supplier）。
     */
    private String supplierCode;

    /**
     * 入库日期 yyyy-MM-dd。
     */
    private String inboundDate;

    /**
     * 来源单据号（写入 remark）。
     */
    private String sourceBill;

}
