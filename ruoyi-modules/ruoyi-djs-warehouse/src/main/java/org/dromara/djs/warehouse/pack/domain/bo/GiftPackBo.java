package org.dromara.djs.warehouse.pack.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 礼盒打包 BO（WMS-PACK-001）。
 *
 * <p>礼盒为独立成品：打 N 盒只产出 N 盒礼盒成品，不查/不消耗任何组件（BOM）。本 BO 仅传
 * {@code giftBoxProductId} + {@code packBoxCount}，service 端按 box_product_id 写主 production 记录。</p>
 *
 * <p>事务：</p>
 * <ol>
 *   <li>校验礼盒 SKU 存在 + 是礼盒（belong_type=gift_box）</li>
 *   <li>INSERT product_production（pack_status='packed'，produceNo L+前缀）</li>
 *   <li>INSERT stock_flow (flow_type='pack_in', IN, 入礼盒目标库位)</li>
 *   <li>UPDATE location_stock += packBoxCount（目标库位）</li>
 * </ol>
 *
 * @author djs
 * @since WMS-PACK-001
 */
@Data
public class GiftPackBo {

    /**
     * 礼盒 SKU ID（FK → {@code t_warehouse_product_info.id}，必须 belong_type=gift_box）。
     */
    @NotNull(message = "{pack.gift_box_product_id.required}")
    private Long giftBoxProductId;

    /**
     * 打包盒数（&ge; 1）。
     */
    @NotNull(message = "{pack.pack_box_count.required}")
    @Positive(message = "{pack.pack_box_count.positive}")
    private Integer packBoxCount;

    /**
     * 入库目标库位 FK（成品礼盒库）。
     *
     * <p>FIX-WMS-PACK-CASHIER：前端打包收银台不再采集入库库位，可空；service 端默认取该礼盒产品配置的
     * 入库库位（{@code product_info.store_location_id} 首个），无配置则取首个可用库位兜底。</p>
     */
    private Long locationId;

    /**
     * 需求门店 ID（必选，需求 C / 礼盒澄清 2026-06-25）。
     *
     * <p>礼盒是门店下单的生产产品：门店下单礼盒 → 仓库看需求打包 → 礼盒打包即按该门店最早未完成的礼盒
     * 需求扣减盒数（{@code shipped_count}）。故礼盒打包必须选门店。</p>
     */
    @NotNull(message = "{pack.store_id.required}")
    private Long storeId;

    /**
     * 发送位置字典 {@code djs_pack_send_dest}：platform 发货月台 / mail 邮寄 / gift 礼盒（可选）。
     */
    @Pattern(regexp = "^(platform|mail|gift)$", message = "{pack.deliver_dest.invalid}")
    private String deliverDest;

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
