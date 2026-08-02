package org.dromara.djs.warehouse.vegout.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 毛菜间出库-可选产品行（admin row187 新增抽屉左侧列表）。
 *
 * <p>数据源 = 毛菜鲜品库（L0006）里 belong_type='vegetable' 的库存行，
 * 一行一个「产品 × 地块」篮（location_stock 自带 plot_id）。</p>
 *
 * @author djs
 */
@Data
public class VegOutCandidateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 库存行 id（提交时按行出库）。 */
    private Long stockId;

    /** 产品 id。 */
    private Long productId;

    /** 产品名称。 */
    private String productName;

    /** 产品规格。 */
    private String productSpec;

    /** 库存重量（kg）。 */
    private BigDecimal stockWeight;

    /** 计量单位。 */
    private String productUnit;

    /** 地块 id（可空：非按地块入库的行）。 */
    private Long plotId;

    /** 地块编号（冗余展示；plotId 为空时为 null）。 */
    private String plotCode;
}
