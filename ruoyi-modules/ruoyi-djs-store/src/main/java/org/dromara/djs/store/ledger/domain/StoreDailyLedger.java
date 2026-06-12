package org.dromara.djs.store.ledger.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 门店经营流水盘点台账实体（{@code t_store_daily_ledger}，STORE-LEDGER-001）。
 *
 * <p>一行 = 某门店某产品某盘点日的经营六列流水 + 期末库存。客户口径：对仓库发货到门店的产品，
 * 录期初 / 当日入库 / 销售 / 赠送 / 退货 / 退回 / 损耗，相当于门店自己的库存盘点（首次建账即期初）。</p>
 *
 * <p>期末库存公式（service 落库时算）：
 * {@code closing_qty = opening_qty + inbound_qty − sale_qty − gift_qty − return_qty − loss_qty}。</p>
 *
 * @author djs
 * @since STORE-LEDGER-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_store_daily_ledger")
public class StoreDailyLedger extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（雪花）。 */
    @TableId
    private Long id;

    /** 门店 FK → {@code t_md_store.id}。 */
    private Long storeId;

    /** 产品 FK → {@code t_warehouse_product_info.id}。 */
    private Long productId;

    /** 盘点日期（到天）。 */
    private LocalDate ledgerDate;

    /** 期初库存（V1 手填，首次建账即期初）。 */
    private BigDecimal openingQty;

    /** 当日入库量（可用仓库发出量预填，口径=发出非实收）。 */
    private BigDecimal inboundQty;

    /** 销售量（可由 {@code t_store_sale_record} 当日聚合预填）。 */
    private BigDecimal saleQty;

    /** 赠送量（手填）。 */
    private BigDecimal giftQty;

    /** 退货量（顾客退货，可由 {@code t_store_return} 当日聚合预填）。 */
    private BigDecimal returnQty;

    /** 损耗量（手填，未填按 0）。 */
    private BigDecimal lossQty;

    /** 期末库存（service 算：期初+入库−销售−赠送−退货−损耗）。 */
    private BigDecimal closingQty;

    /** 盘点人 user_id → {@code sys_user.user_id}（LoginHelper 注入）。 */
    private Long operatorId;

    /** 备注。 */
    private String remark;

    /** 软删标记。 */
    @TableLogic
    private String delFlag;

    /** 软删唯一辅助列。 */
    private Long delUnique;
}
