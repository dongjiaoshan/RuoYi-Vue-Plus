package org.dromara.djs.warehouse.cut.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 阶段 2 出库称重（多部位提交）BO（WMS-PIG-002）。
 *
 * <p>service 同事务跨表 N+2 步：</p>
 * <ol>
 *   <li>SELECT cut_record 校验 cut_status='picked' or 'cutting'</li>
 *   <li>首次 → UPDATE cut_record status=cutting + cut_start_time + bar_info status=cutting（乐观锁）</li>
 *   <li>for each part：INSERT t_warehouse_product_inhouse + INSERT t_warehouse_stock_flow 入冻品库</li>
 *   <li>INSERT t_warehouse_stock_flow 白条出库（合计 weight）</li>
 * </ol>
 *
 * @author djs
 * @since WMS-PIG-002
 */
@Data
public class PigCutOutBo {

    /**
     * 分割记录 ID（cut_record.id）。
     */
    @NotNull(message = "{cut.cut_record_id.required}")
    private Long cutRecordId;

    /**
     * 入冻品库位（多部位共用）。
     */
    @NotNull(message = "{cut.location_id.required}")
    private Long locationId;

    /**
     * 多部位明细（至少 1 行）。
     */
    @NotEmpty(message = "{cut.part_items.empty}")
    @Valid
    private List<PartItem> partItems;

    /**
     * 凭证图 OSS IDs CSV（可选）。
     */
    @Size(max = 500, message = "{cut.proof_oss_ids.size}")
    private String proofOssIds;

    /**
     * 单部位明细。
     *
     * <p><b>按具体产品对齐原型（Kevin 定，DJS-FIX-WMS-PACK）</b>：分割间产出按
     * {@code productId}（产品主数据里的猪肉分割成品，如冻五花肉 / 排骨 / 肘子 / 大排）录入。
     * service 端 {@code productId} 非空时直接用该产品落库（name/unit 取自 product_info）；
     * {@code productId} 为空时回落 {@code cutPart} 5 部位枚举解析标准 SKU（向后兼容 mp 旧端 +
     * 分割成品主数据未 seed 场景）。两者至少传一个，service 端校验。</p>
     */
    @Data
    public static class PartItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 分割成品产品 ID（FK → {@code t_warehouse_product_info.id}，belong_type=pork）。
         *
         * <p>原型分割间产品卡（冻五花肉 / 排骨 / 肘子 / 大排 等）对应的具体产品。
         * 与 {@link #cutPart} 二选一：非空优先按具体产品落库。</p>
         */
        private Long productId;

        /**
         * 分割部位字典 key（{@code djs_pig_cut_part}：lean/part/bone/skin/scrap）。
         *
         * <p>{@link #productId} 为空时使用（向后兼容；按部位解析标准 SKU）。</p>
         */
        @Pattern(regexp = "^(lean|part|bone|skin|scrap)$", message = "{cut.cut_part.invalid}")
        private String cutPart;

        /**
         * 重量 kg（&gt; 0）。
         */
        @NotNull(message = "{cut.product_weight.required}")
        @DecimalMin(value = "0.001", message = "{cut.product_weight.positive}")
        private BigDecimal productWeight;

        /**
         * 规格（可选）。
         */
        @Size(max = 64, message = "{cut.product_spec.size}")
        private String productSpec;

    }

}
