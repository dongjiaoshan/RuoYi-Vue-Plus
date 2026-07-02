package org.dromara.djs.warehouse.pack.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 白条/猪肉「仓库出库」BO（row17）。
 *
 * <p>白条领用页第 3 个出库位置「仓库出库」：把白条整只(燎毛产)/猪肉部位(分割产)的
 * {@code product_inhouse} 出库（区别于发货月台出库=发往某门店、分割车间=进分割）。
 * 出库不发往门店，而是记「出库去向」（字典 {@code djs_bar_out_dest}，如其他仓库 / 暂存冷库 / 损耗处理），
 * 出库方式=后台出库。复用 {@code submitWhiteBarOut} 收尾范式，但 <b>不绑门店、不扣门店需求</b>。</p>
 *
 * @author djs
 * @since row17
 */
@Data
public class WarehouseOutBo {

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
     * 出库去向（字典 {@code djs_bar_out_dest} 的 dict_value）。
     */
    @NotBlank(message = "请选择出库去向")
    @Size(max = 32, message = "出库去向长度不能超过 32")
    private String outDest;

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
