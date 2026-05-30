package org.dromara.djs.warehouse.pack.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 蔬菜打包 BO（WMS-PACK-001）。
 *
 * <p>事务：</p>
 * <ol>
 *   <li>校验 {@code sourceInhouseId}（{@code t_warehouse_product_inhouse}）剩余数量足够</li>
 *   <li>UPDATE product_inhouse 扣减来源（V1 不拆细到 location_stock，参 D9 范式）</li>
 *   <li>INSERT product_production（packStatus='packed'，produceNo G+前缀）</li>
 *   <li>INSERT stock_flow (flow_type='pack_in')</li>
 *   <li>UPDATE location_stock += quantity（如有 location_id）</li>
 * </ol>
 *
 * @author djs
 * @since WMS-PACK-001
 */
@Data
public class VegPackBo {

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
     * 打包重量 kg（&gt; 0）。
     */
    @NotNull(message = "{pack.product_weight.required}")
    @DecimalMin(value = "0.001", message = "{pack.product_weight.positive}")
    private BigDecimal productWeight;

    /**
     * 入库目标库位 FK → {@code t_warehouse_location_info.id}（蔬菜鲜品库）。
     */
    @NotNull(message = "{pack.location_id.required}")
    private Long locationId;

    /**
     * 包材原材料消耗 kg（可选）。
     */
    @DecimalMin(value = "0", inclusive = true, message = "{pack.material_consume.non_negative}")
    private BigDecimal materialConsume;

    /**
     * 包材产品 ID（可选）。
     */
    private Long materialId;

    /**
     * 需求门店 ID（可选）。
     */
    private Long storeId;

    /**
     * 规格（可选）。
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
