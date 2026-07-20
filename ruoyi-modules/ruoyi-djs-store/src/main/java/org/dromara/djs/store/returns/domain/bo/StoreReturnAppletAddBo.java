package org.dromara.djs.store.returns.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店退回（门店→仓库）mp 退货录入 BO（STORE-RETURN-UNIFY-001，对齐 mp ReturnAddBody）。
 *
 * <p>仓库工人在小程序主动登记一条门店退货（pending，不入库）→ 统一落 {@code t_store_return}
 * （store_to_warehouse），与门店「退回操作」同一真相源，避免再次双表割裂。</p>
 *
 * @author djs
 * @since STORE-RETURN-UNIFY-001
 */
@Data
public class StoreReturnAppletAddBo {

    /** 退回门店（必填）。 */
    @NotNull(message = "退回门店不能为空")
    private Long storeId;

    /** 产品 FK → {@code t_warehouse_product_info.id}（必填）。 */
    @NotNull(message = "产品不能为空")
    private Long productId;

    /** 退回重量(kg)（落 goods_weight，必填）。 */
    @NotNull(message = "退回重量不能为空")
    @Positive(message = "退回重量必须大于 0")
    private BigDecimal returnWeight;

    /** 退回量（果蔬份数/把/盒，落 return_quantity；空时按重量回退）。 */
    @Positive(message = "退回量必须大于 0")
    private BigDecimal returnQuantity;

    /** 已贴追溯码（可空）。 */
    @Size(max = 64, message = "追溯码长度不能超过 64")
    private String traceCode;
}
