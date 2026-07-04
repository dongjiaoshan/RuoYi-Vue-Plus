package org.dromara.djs.store.returns.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店退回仓库确认实收 BO（STORE-RETURN-REALIGN-001，对齐原型「退回记录」仓库确认入库）。
 *
 * <p>仓库复核报退记录后填实收量/实收重量 + 选入库库位，状态 {@code pending → received}，
 * <b>此时才联动外购入库</b>（{@code location_stock} + {@code stock_flow(return_in)}）。</p>
 *
 * @author djs
 * @since STORE-RETURN-REALIGN-001
 */
@Data
public class StoreReturnConfirmBo {

    /** 退回记录主键。 */
    @NotNull(message = "退回记录 ID 不能为空")
    private Long id;

    /**
     * 入库库位 FK → {@code t_warehouse_location_info.id}（确认实收时可选）。
     * 为空时由 service 按入库产品的预设库位 / 库存最多库位自动兜底（mp 确认页只填实收量、不选库位）。
     */
    private Long locationId;

    /** 仓库实收量（入库到 location_stock 的数量）。 */
    @NotNull(message = "仓库实收量不能为空")
    @Positive(message = "仓库实收量必须大于 0")
    private BigDecimal receivedQty;

    /** 仓库实收重量(kg)（原型「仓库实收重量」，可空时按实收量计）。 */
    private BigDecimal receivedWeight;

    /**
     * 猪肉退货入库库位类型（row145.3）：{@code fresh}=猪肉鲜品库 / {@code frozen}=冻品库。
     * 非空且退货产品为 pork 时，覆盖默认库位；否则忽略（走 locationId / 默认兜底）。
     */
    private String targetLocationType;
}
