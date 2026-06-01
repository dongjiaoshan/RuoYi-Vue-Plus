package org.dromara.djs.warehouse.check.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 提交 / 修改实盘明细 line 入参 BO（WMS-STOCK-001 mp 端）。
 *
 * <p>mp 盘点员选产品（{@code ProductPicker}）→ 录入实盘量 + 差异原因（可选）→ 提交。
 * 后端按 {@code checkId + locationId + productId} upsert 一条 line（{@code isHeader=0}），
 * 自动算 {@code sysStock}（查 location_stock 现量）+ {@code diffStock = checkStock - sysStock}。</p>
 *
 * <p>跨层契约 §1：{@code checkId} 业务码 string；{@code productId} snowflake string，mp body 全 string 提交。</p>
 *
 * @author djs
 * @since WMS-STOCK-001
 */
@Data
public class StockCheckLineBo {

    /**
     * 修改场景的 line 主键（snowflake string；提交新 line 时留空）。
     */
    private Long id;

    /**
     * 盘点单业务码（关联 header；mp body 传 string）。
     */
    @NotBlank(message = "盘点单号不能为空")
    @Size(max = 32, message = "盘点单号长度不能超过 32")
    private String checkId;

    /**
     * 产品 ID（snowflake；mp body 传 string，后端 Jackson 转 Long）。
     */
    @NotNull(message = "盘点产品不能为空")
    private Long productId;

    /**
     * 实盘量（必填，&ge; 0）。
     */
    @NotNull(message = "实盘量不能为空")
    private BigDecimal checkStock;

    /**
     * 差异原因（可选）。
     */
    @Size(max = 255, message = "差异原因长度不能超过 255")
    private String diffReason;

    /**
     * 盘点结果类型（字典 {@code djs_check_result}：1=正常 / 2=异常 / 3=计损；mp 默认按差异自动判定，可手填覆盖）。
     */
    private Integer checkResultType;

}
