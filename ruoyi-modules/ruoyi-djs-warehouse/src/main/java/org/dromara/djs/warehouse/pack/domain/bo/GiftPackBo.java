package org.dromara.djs.warehouse.pack.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 礼盒打包 BO（WMS-PACK-001）。
 *
 * <p><b>Kevin override 决策 b 已落表</b>：组合明细复用 D8 {@code t_warehouse_gift_box} 关联表
 * （{@code box_product_id} 礼盒 SKU + N 行 {@code component_product_id} 组件 SKU + count + unit）。
 * 本 BO 仅传 {@code giftBoxProductId} + {@code packBoxCount}，service 端按 box_product_id 查
 * gift_box 拿组件清单，扣减来源 product_inhouse / location_stock，写主 production 记录。</p>
 *
 * <p>事务：</p>
 * <ol>
 *   <li>SELECT gift_box WHERE box_product_id={giftBoxProductId}，拿组件清单 N 行</li>
 *   <li>for each component：校验 location_stock 足够（按 productId+locationId）</li>
 *   <li>for each component：UPDATE location_stock -= component_count * packBoxCount + INSERT stock_flow (flow_type='pack_consume', OT)</li>
 *   <li>INSERT product_production（productType=3 礼盒，pack_status='packed'，produceNo L+前缀）</li>
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
     * 礼盒 SKU ID（FK → {@code t_warehouse_product_info.id}，必须 product_type=3）。
     *
     * <p>service 按此 ID 查 {@code t_warehouse_gift_box} 拿组件清单。</p>
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
     */
    @NotNull(message = "{pack.location_id.required}")
    private Long locationId;

    /**
     * 需求门店 ID（可选）。
     */
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
