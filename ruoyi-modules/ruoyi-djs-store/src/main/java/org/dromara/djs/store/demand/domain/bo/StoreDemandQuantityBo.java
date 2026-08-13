package org.dromara.djs.store.demand.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店改需求量 / 删行 BO（mp 需求详情页，V6 row70）。
 *
 * <p>{@code demandQuantity > 0} = 改量；{@code = 0} = 删除该行（走既有删除路径：置 DELETED 终态 + 软删）。
 * 只允许门店态 {@code SUBMITTED}（待确认）的行，其余一律拒绝。</p>
 *
 * @author djs
 * @since STORE-MP-BOARD-001
 */
@Data
public class StoreDemandQuantityBo {

    /** 需求 ID（{@code t_warehouse_demand_manage.id}，snowflake）。 */
    @NotNull(message = "需求 ID 不能为空")
    private Long id;

    /**
     * 需求量（≥ 0；0 = 删除该行）。
     *
     * <p>{@code @Digits} 对齐库表 {@code demand_quantity DECIMAL(12,3)}：不加的话
     * 4 位小数会被 MySQL <b>静默四舍五入</b>（1.2345 落成 1.235，用户不知情），
     * 而超 12 位整数部分会漏成通用 500「发生未知异常」。两种都在这里挡成明确中文提示。</p>
     */
    @NotNull(message = "需求量不能为空")
    @DecimalMin(value = "0", message = "需求量不能为负数")
    @Digits(integer = 9, fraction = 3, message = "需求量最多 9 位整数、3 位小数")
    private BigDecimal demandQuantity;
}
