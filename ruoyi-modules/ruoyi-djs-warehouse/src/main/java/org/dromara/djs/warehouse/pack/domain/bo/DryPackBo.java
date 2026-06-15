package org.dromara.djs.warehouse.pack.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 干货打包 BO（WMS-PACK-001）。
 *
 * <p>事务同 {@link VegPackBo}（无地块来源），但 produceNo 前缀 = H（干货）。</p>
 *
 * @author djs
 * @since WMS-PACK-001
 */
@Data
public class DryPackBo {

    /**
     * 来源过程产品 ID（FK → {@code t_warehouse_product_inhouse.id}）。
     */
    @NotNull(message = "{pack.source_inhouse_id.required}")
    private Long sourceInhouseId;

    /**
     * 目标打包产品 ID（FK → {@code t_warehouse_product_info.id}，必须 is_delivery=1）。
     */
    @NotNull(message = "{pack.product_id.required}")
    private Long productId;

    /**
     * 打包重量 / 数量（&gt; 0）。
     */
    @NotNull(message = "{pack.product_weight.required}")
    @DecimalMin(value = "0.001", message = "{pack.product_weight.positive}")
    private BigDecimal productWeight;

    /**
     * 计量单位 {@code kg / 个}。
     */
    @NotBlank(message = "{pack.product_unit.required}")
    @Size(max = 16, message = "{pack.product_unit.size}")
    private String productUnit;

    /**
     * 入库目标库位 FK → {@code t_warehouse_location_info.id}（干货库）。
     *
     * <p>FIX-WMS-PACK-CASHIER：前端打包收银台不再采集入库库位，可空；service 端默认取该产品配置的
     * 入库库位（{@code product_info.store_location_id} 首个），无配置则取首个可用库位兜底。</p>
     */
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
     * 规格（如 250g/包）。
     */
    @Size(max = 64, message = "{pack.product_spec.size}")
    private String productSpec;

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
