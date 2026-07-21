package org.dromara.djs.store.returns.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门店退回操作批量录入 BO（STORE-RETURN-REALIGN-001，对齐原型「退回操作」整页矩阵提交）。
 *
 * <p>一次提交某门店多条退回（按门店关联产品逐行录退回重量），均建为 {@code pending}（待仓库确认），
 * <b>不联动入库</b>——库存联动延后到仓库确认实收（{@link StoreReturnConfirmBo}）。</p>
 *
 * @author djs
 * @since STORE-RETURN-REALIGN-001
 */
@Data
public class StoreReturnBatchBo {

    /** 退回门店（顾客→门店主场景必填）。 */
    @NotNull(message = "退回门店不能为空")
    private Long storeId;

    /** 退回明细行（至少 1 行）。 */
    @Valid
    @NotEmpty(message = "退回明细不能为空")
    private List<Item> items;

    /**
     * 退回明细单行：产品 + 退回重量（kg）。
     *
     * @author djs
     * @since STORE-RETURN-REALIGN-001
     */
    @Data
    public static class Item {

        /** 产品 FK → {@code t_warehouse_product_info.id}。 */
        @NotNull(message = "产品不能为空")
        private Long productId;

        /**
         * 退回产品重量(kg)（落 goods_weight）。流程性问题 row15：不再单列录入，由前端按单位派生——
         * 产品单位为 kg → 取退回量的值；产品单位非 kg → 默认 0。故此处放开为可空/可 0。
         */
        private BigDecimal returnWeight;

        /**
         * 退回量（按产品单位：kg 产品为重量、非 kg 产品为份数/把/盒，落 return_quantity）。
         * 流程性问题 row15 起为唯一录入项，必填。
         */
        @NotNull(message = "退回量不能为空")
        @Positive(message = "退回量必须大于 0")
        private BigDecimal returnQuantity;

        /** 已贴追溯码（可空），V1 仅存值无校验。 */
        @Size(max = 64, message = "追溯码长度不能超过 64")
        private String traceCode;
    }
}
