package org.dromara.djs.warehouse.selfcheck.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 盘点录入提交 BO（mp 库存盘点子系统 §3.C.7，方案 A：每次盘点都写 flow 留痕）。
 *
 * <p>字段名对齐 mp 契约 {@code stockCheck.ts} StockCheckEntryBody。结果联动：正常无量；
 * 计损/异常带量 + 原因。</p>
 *
 * @author djs
 * @since SELFCHECK
 */
@Data
public class StockCheckEntryBo implements Serializable {

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
     * 盘点人员 userId（snowflake string，可空，为空取当前登录人）。
     */
    private String operatorId;

    /**
     * 实盘 / 当前库存量。
     */
    @NotNull(message = "实盘量不能为空")
    private BigDecimal checkStock;

    /**
     * 盘点结果 normal 正常 / loss 计损 / abnormal 异常。
     */
    @NotBlank(message = "盘点结果不能为空")
    private String checkResult;

    /**
     * 计损量 / 异常量（结果非正常时有值）。
     */
    private BigDecimal diffQuantity;

    /**
     * 计损原因 / 异常原因（写入 remark）。
     */
    private String diffReason;

    /**
     * 盘点日期 yyyy-MM-dd。
     */
    private String checkDate;

}
