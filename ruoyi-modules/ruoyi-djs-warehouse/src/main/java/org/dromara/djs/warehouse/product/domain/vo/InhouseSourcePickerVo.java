package org.dromara.djs.warehouse.product.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 过程产品来源 picker 轻量 VO（MP-PICKERS-001）。
 *
 * <p>给 mp {@code InhouseSourcePicker} 用——打包 / 出库时选可用的过程产品（冻品库 / 鲜品库）
 * 作为原料来源。对应表 {@code t_warehouse_product_inhouse}。</p>
 *
 * <p>{@code weight} 取自 {@code product_weight}；{@code locationCode} 由 controller 批量
 * JOIN {@code t_warehouse_location_info.location_code} 填充（inhouse 主表只存 location_id）。</p>
 *
 * @author djs
 * @since MP-PICKERS-001
 */
@Data
public class InhouseSourcePickerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 过程产品 ID（snowflake，Jackson 全局序列化为 string）。
     */
    private Long id;

    /**
     * 产品名称（inhouse 主表冗余字段）。
     */
    private String productName;

    /**
     * 产品重量 kg（取自 {@code product_weight}）。
     */
    private BigDecimal weight;

    /**
     * 计量单位（kg / 个）。
     */
    private String unit;

    /**
     * 入库库位业务码（由 location_id 关联 {@code t_warehouse_location_info.location_code} 填充）。
     */
    private String locationCode;

}
