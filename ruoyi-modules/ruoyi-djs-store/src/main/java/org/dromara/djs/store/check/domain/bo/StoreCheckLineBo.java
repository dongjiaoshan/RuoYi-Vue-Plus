package org.dromara.djs.store.check.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 提交 / 修改实盘明细 line 入参 BO（STR-STOCK-001 admin 详情页录入，无 mp）。
 *
 * <p>admin 详情页选产品 → 录入实盘量 + 差异原因（可选）→ 提交。后端按 {@code checkId + storeId + productId}
 * upsert 一条 line（{@code isHeader=0}），自动算 {@code sysStock}（门店该产品系统量）+
 * {@code diffStock = checkStock - sysStock}。</p>
 *
 * @author djs
 * @since STR-STOCK-001
 */
@Data
public class StoreCheckLineBo {

    /**
     * 修改场景的 line 主键（snowflake；提交新 line 时留空）。
     */
    private Long id;

    /**
     * 盘点单业务码（关联 header）。
     */
    @NotBlank(message = "盘点单号不能为空")
    @Size(max = 32, message = "盘点单号长度不能超过 32")
    private String checkId;

    /**
     * 产品 ID（snowflake）。
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
     * 盘点结果类型（字典 {@code djs_check_result}：1=正常 / 2=异常 / 3=计损；默认按差异自动判定，可手填覆盖）。
     */
    private Integer checkResultType;

}
