package org.dromara.djs.warehouse.pack.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 白条/猪肉出库(发货领用) BO（WMS-WHITEBAR-SHIP-001）。
 *
 * <p>把白条整只(燎毛产)/猪肉部位(分割产)的 {@code product_inhouse} 出库为
 * {@code product_production}（发货产品，produce_no 前缀 B=白条 / Z=猪肉）。
 * 区别于 4 个打包口：<b>不选目标发货 SKU</b> —— 直接用来源 inhouse 自身 product_id
 * （白条整只/半只 SKU is_delivery=0 是燎毛过程态，出库即发货，shipment 靠
 * {@code product_info.belong_type ∈ {white_bar,pork}} 匹配，非 is_delivery）。</p>
 *
 * @author djs
 * @since WMS-WHITEBAR-SHIP-001
 */
@Data
public class WhiteBarOutBo {

    /**
     * 来源白条/猪肉过程产品 ID（FK → {@code t_warehouse_product_inhouse.id}）。
     */
    @NotNull(message = "{pack.source_inhouse_id.required}")
    private Long sourceInhouseId;

    /**
     * 出库重量（kg，&gt; 0）。
     */
    @NotNull(message = "{pack.product_weight.required}")
    @DecimalMin(value = "0.001", message = "{pack.product_weight.positive}")
    private BigDecimal productWeight;

    /**
     * 指定门店 ID（发货月台领用必填——发货月台关联门店；工人 StorePicker 选）。
     *
     * <p>猪肉全闭环 Part I P6：发货月台出库即发往某门店，故由可空收紧为必填 + service 校验门店存在。</p>
     */
    @NotNull(message = "请选择发货门店")
    private Long storeId;

    /**
     * 凭证图 OSS IDs CSV（可选）。
     */
    @Size(max = 500, message = "{pack.proof_oss_ids.size}")
    private String proofOssIds;

    /**
     * 备注。
     */
    @Size(max = 500, message = "{pack.remark.size}")
    private String remark;

}
