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
     */
    @Data
    public static class PartItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 分割部位字典 key（{@code djs_pig_cut_part}：lean/part/bone/skin/scrap）。
         */
        @NotNull(message = "{cut.cut_part.required}")
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
