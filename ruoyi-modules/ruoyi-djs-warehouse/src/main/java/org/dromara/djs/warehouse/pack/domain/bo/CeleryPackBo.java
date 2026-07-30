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
     * 需求门店 ID（必选，需求 C）。芹菜打包无礼盒发送位置、恒直接履约 → 始终必选门店。
     *
     * <p>打包必须选中对应门店：打包即按该门店最早未完成需求扣减 shipped_count（替代原发货扣减，防双扣），
     * 追溯码也按此门店归属（{@code trace_code.store_id}）。</p>
     */
    @NotNull(message = "{pack.store_id.required}")
    private Long storeId;

    /**
     * 实称超出打包规则上限时，操作员已明确确认继续。
     *
     * <p>只对「超出」生效；实称低于规则重量是硬拒，这个标记放不过去
     * （见 {@code ProductProductionServiceImpl#validatePackMeasureRule}）。</p>
     */
    private Boolean allowOverMeasure;

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
