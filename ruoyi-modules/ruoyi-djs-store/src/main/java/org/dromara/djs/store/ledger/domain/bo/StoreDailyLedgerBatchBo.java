package org.dromara.djs.store.ledger.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 当日盘点整表批量提交 BO（STORE-LEDGER-001 / WSA 重构，对齐原型「门店盘点>新增当日盘点」整页大表）。
 *
 * <p>一次提交某门店某日多产品行的经营流水。同门店同产品同日唯一（UNIQUE 约束保证）。</p>
 *
 * <p>口径变更（WSA 阶段1，按 docx 字面）：
 * <ul>
 *   <li>{@code closingQty}（期末）改为<b>手动入参</b>（实盘录入）；</li>
 *   <li>{@code lossQty}（损耗）改为 service <b>计算</b>：
 *       {@code loss = opening + inbound − sale − gift + returnSale − returnWh − closing}（量列缺省 0）；</li>
 *   <li>{@code inboundQty}（新到货）：猪肉行手动录入（上限 = 当日白条发货重量）；非猪肉「新到货」行为发货量预填的只读值；</li>
 *   <li>{@code returnWhQty}（退回）来自现有门店退回聚合，只读，前端回传以备重算校验。</li>
 * </ul>
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

    /**
     * 是否更正已盘记录（DENGBO-R13）：
     * <ul>
     *   <li>{@code null}/{@code false} = 新增当日盘点，此时该门店该日<b>已有盘点记录则拒绝</b>（同一天不能重复盘点）；</li>
     *   <li>{@code true} = 「修改」入口更正上次盘点结果，允许覆盖已有记录。</li>
     * </ul>
     */
    private Boolean edit;

    /** 备注。 */
    private String remark;

    /** 盘点明细行（至少 1 行）。 */
    @Valid
    @NotEmpty(message = "盘点明细不能为空")
    private List<Item> items;

    /**
     * 盘点明细单行：产品 + 经营流水列（量列缺省按 0）。
     *
     * <p>损耗 {@code lossQty} 不在 BO（由 service 按公式计算），期末 {@code closingQty} 手动入参。</p>
     *
     * @author djs
     * @since STORE-LEDGER-001
     */
    @Data
    public static class Item {

        /** 产品 FK → {@code t_warehouse_product_info.id}。 */
        @NotNull(message = "产品不能为空")
        private Long productId;

        /** 期初库存（只读，来自库存表当前结存；前端回传以备落库一致）。 */
        private BigDecimal openingQty;

        /** 当日入库量（新到货）：猪肉行手动录入，非猪肉行为发货量只读预填。 */
        private BigDecimal inboundQty;

        /** 销售量（手动，默认 0）。 */
        private BigDecimal saleQty;

        /** 赠送量（手动，默认 0）。 */
        private BigDecimal giftQty;

        /** 退货量（顾客退货，手动，默认 0；映射 entity returnQty 列）。 */
        private BigDecimal returnSaleQty;

        /** 退回量（门店退回仓库，只读来自退回模块聚合；映射 entity whReturnQty 列）。 */
        private BigDecimal returnWhQty;

        /** 期末库存（手动实盘录入；service 据此反算损耗）。 */
        private BigDecimal closingQty;
    }
}
