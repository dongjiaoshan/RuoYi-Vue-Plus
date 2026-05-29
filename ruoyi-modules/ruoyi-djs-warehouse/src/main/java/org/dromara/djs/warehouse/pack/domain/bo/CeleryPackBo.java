package org.dromara.djs.warehouse.pack.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 芹菜按重量打包 BO（WMS-PACK-001）。
 *
 * <p>芹菜业务特殊：无规格，按重量。前缀 G（果蔬，与蔬菜同段）。
 * service 端自动写 {@code product_spec='按重量'}。</p>
 *
 * @author djs
 * @since WMS-PACK-001
 */
@Data
public class CeleryPackBo {

    /**
     * 来源过程产品 ID（FK → {@code t_warehouse_product_inhouse.id}）。
     */
    @NotNull(message = "{pack.source_inhouse_id.required}")
    private Long sourceInhouseId;

    /**
     * 目标打包产品 ID（FK → {@code t_warehouse_product_info.id}）。
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
     * 入库目标库位 FK。
     */
    @NotNull(message = "{pack.location_id.required}")
    private Long locationId;

    /**
     * 需求门店 ID（可选）。
     */
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
