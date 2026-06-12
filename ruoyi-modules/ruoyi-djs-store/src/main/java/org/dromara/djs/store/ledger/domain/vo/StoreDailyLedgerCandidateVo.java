package org.dromara.djs.store.ledger.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 当日盘点候选行 VO（STORE-LEDGER-001「新增当日盘点」GET 候选）。
 *
 * <p>列出门店关联产品全 SKU，并预填可推导的量列：
 * {@code saleQty}（{@code t_store_sale_record} 当日聚合）、
 * {@code returnQty}（{@code t_store_return} customer_to_store 当日聚合）、
 * {@code whReturnQty}（{@code t_store_return} store_to_warehouse 当日聚合，原型只读）。
 * 期初 / 当日入库 / 赠送 / 损耗前端手填默认 0。</p>
 *
 * @author djs
 * @since STORE-LEDGER-001
 */
@Data
public class StoreDailyLedgerCandidateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品 ID。 */
    private Long productId;

    /** 产品名称。 */
    private String productName;

    /** 产品单位。 */
    private String productUnit;

    /** 产品规格。 */
    private String productSpec;

    /** 预填销售量（销售流水当日聚合，无则 0）。 */
    private BigDecimal saleQty;

    /** 预填入库量（仓库发出量当日聚合，无则 0）。 */
    private BigDecimal inboundQty;

    /** 预填退货量（顾客退货 customer_to_store 当日聚合，无则 0）。 */
    private BigDecimal returnQty;

    /** 预填退回量（门店退回仓库 store_to_warehouse 当日聚合，无则 0；原型只读）。 */
    private BigDecimal whReturnQty;
}
