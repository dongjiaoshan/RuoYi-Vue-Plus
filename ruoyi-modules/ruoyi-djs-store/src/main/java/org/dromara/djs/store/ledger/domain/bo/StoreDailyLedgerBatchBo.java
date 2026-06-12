package org.dromara.djs.store.ledger.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 当日盘点整表批量提交 BO（STORE-LEDGER-001，对齐原型「门店盘点>新增当日盘点」整页大表）。
 *
 * <p>一次提交某门店某日多产品行的经营六列流水。同门店同产品同日唯一（UNIQUE 约束保证），
 * service 落库时算 {@code closing_qty}，未填的量列按 0 处理。</p>
 *
 * @author djs
 * @since STORE-LEDGER-001
 */
@Data
public class StoreDailyLedgerBatchBo {

    /** 盘点门店。 */
    @NotNull(message = "盘点门店不能为空")
    private Long storeId;

    /** 盘点日期（缺省 service 用今天）。 */
    private LocalDate ledgerDate;

    /** 备注。 */
    private String remark;

    /** 盘点明细行（至少 1 行）。 */
    @Valid
    @NotEmpty(message = "盘点明细不能为空")
    private List<Item> items;

    /**
     * 盘点明细单行：产品 + 经营六列流水（量列缺省按 0）。
     *
     * @author djs
     * @since STORE-LEDGER-001
     */
    @Data
    public static class Item {

        /** 产品 FK → {@code t_warehouse_product_info.id}。 */
        @NotNull(message = "产品不能为空")
        private Long productId;

        /** 期初库存。 */
        private BigDecimal openingQty;

        /** 当日入库量。 */
        private BigDecimal inboundQty;

        /** 销售量。 */
        private BigDecimal saleQty;

        /** 赠送量。 */
        private BigDecimal giftQty;

        /** 退货量（顾客退货）。 */
        private BigDecimal returnQty;

        /** 退回量（门店退回仓库，原型只读，未填按 0）。 */
        private BigDecimal whReturnQty;

        /** 损耗量（未填按 0）。 */
        private BigDecimal lossQty;
    }
}
