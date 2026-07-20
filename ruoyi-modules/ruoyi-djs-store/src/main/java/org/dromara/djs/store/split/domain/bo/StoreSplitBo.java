package org.dromara.djs.store.split.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店白条再分录入入参 BO（STR-SPLIT-001 admin 单页录入，无 mp）。
 *
 * <p>店长把仓库已分割入冻品库的白条 / 部位品在门店端再分一次：选部位 + 录重量 + 库位 + 可选源白条溯源
 * → 后端 INSERT 一行 {@code t_warehouse_product_inhouse}（{@code source='store'}），按 {@code cutPart}
 * 反查标准 SKU 回填 {@code productId / productName}。</p>
 *
 * <p>V1 最薄版本：纯独立录入新行，不扣减源部位重量、不做重量勾稽、不写库存流水。</p>
 *
 * @author djs
 * @since STR-SPLIT-001
 */
@Data
public class StoreSplitBo {

    /**
     * 分割部位字典 {@code djs_pig_cut_part}：lean / part / bone / skin / scrap。
     */
    @NotBlank(message = "分割部位不能为空")
    @Size(max = 32, message = "分割部位长度不能超过 32")
    private String cutPart;

    /**
     * 再分重量 kg（必填，&gt; 0）。
     */
    @NotNull(message = "再分重量不能为空")
    @DecimalMin(value = "0.001", message = "再分重量必须大于 0")
    private BigDecimal productWeight;

    /**
     * 入库库位 FK → {@code t_warehouse_location_info.id}（门店库位，可选）。
     */
    private Long locationId;

    /**
     * 源白条溯源关联（可空，snowflake 走 string；service 内 {@code Long.valueOf} 转回实体 {@code white_bar_id} 列）。
     */
    @Size(max = 32, message = "源白条标识长度不能超过 32")
    private String whiteBarId;

    /**
     * 备注（可选）。
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
