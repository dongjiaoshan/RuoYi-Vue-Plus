package org.dromara.djs.store.ledger.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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
 *   <li>{@code lossQty}（损耗）：<b>非猪肉原材料行</b>由 service <b>计算</b>
 *       {@code loss = opening + inbound − sale − gift + returnSale − returnWh − closing}（量列缺省 0，<b>可为负</b>）；
 *       <b>猪肉原材料行</b>（V6-R215）改为前端手填、service 采信；</li>
 *   <li>{@code inboundQty}（新到货）：猪肉行手动录入（上限 = 当日白条发货重量）；非猪肉「新到货」行为到店量预填的只读值；</li>
 *   <li>{@code returnWhQty}（退回）：<b>非猪肉原材料行</b>取现有门店退回聚合、只读，前端回传以备重算校验；
 *       <b>猪肉原材料行</b>（V6-R215：{@code belong_type ∈ (pork, white_bar) 且 product_attr=2}）该列由 service
 *       按 {@code opening + inbound − sale} 倒算残差，<b>前端回传值被忽略</b>。</li>
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
     * <p>量列的「谁手填、谁倒算」按行分流（V6-R215）：
     * <b>猪肉原材料行</b>期末 + 损耗都手填（默认 0），退回量由 service 倒算；
     * <b>其余行</b>期末手填、损耗由 service 倒算，退回量沿用退回模块聚合。</p>
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

        /**
         * 当日入库量（新到货）：猪肉行手动录入，非猪肉行为发货量只读预填。
         *
         * <p>⚠️ 下面这些「量」列一律补 {@code @DecimalMin("0")} —— row215-F4 的服务端闸：前端每格虽然都
         * {@code :min="0"}，接口层原来完全不拦，实测 {@code wh_return_qty=-3.000} 能直接落库。前端闸只挡手滑、
         * 不挡直接打接口；数量列为负在这套盘点模型里没有语义。{@code openingQty} 刻意**不加**：它是服务端回显
         * 的历史结存，老数据可能带负值，加了会把一次正常的「修改」直接 400 挡死。</p>
         */
        @DecimalMin(value = "0", message = "当日入库量不能为负数")
        private BigDecimal inboundQty;

        /** 销售量：普通行手填；猪肉原材料行回传页面看到的打包消耗量，保存时与最新量核对。缺省按 0。 */
        @DecimalMin(value = "0", message = "销售量不能为负数")
        private BigDecimal saleQty;

        /** 赠送量（手动，默认 0）。 */
        @DecimalMin(value = "0", message = "赠送量不能为负数")
        private BigDecimal giftQty;

        /** 退货量（顾客退货，手动，默认 0；映射 entity returnQty 列）。 */
        @DecimalMin(value = "0", message = "顾客退货量不能为负数")
        private BigDecimal returnSaleQty;

        /**
         * 退回量（门店退回仓库；映射 entity whReturnQty 列）。
         *
         * <p>猪肉原材料行由 service 倒算、**忽略本字段**；其余行服务端直接采信本字段 —— 这正是 row215-F4
         * 实测被打穿的那一格，故补非负闸。</p>
         */
        @DecimalMin(value = "0", message = "退回量不能为负数")
        private BigDecimal returnWhQty;

        /** 期末库存（手动实盘录入，默认 0）。 */
        @DecimalMin(value = "0", message = "期末库存不能为负数")
        private BigDecimal closingQty;

        /**
         * 损耗量（V6-R215 起**猪肉原材料行**手动录入，默认 0；其余行忽略本字段、由 service 按恒等式倒算）。
         *
         * <p>猪肉原材料行的退回量反过来成了倒算项：退回量 = 期初+入库−销售−赠送−期末−损耗。</p>
         *
         * <p>非猪肉行 service 倒算出来的损耗<b>允许为负</b>（页面对负损耗另有警示样式），故这里只约束客户端
         * 提交值，不动倒算结果。</p>
         */
        @DecimalMin(value = "0", message = "损耗量不能为负数")
        private BigDecimal lossQty;
    }
}
