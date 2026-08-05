package org.dromara.djs.warehouse.vegout.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 毛菜间出库单明细行（admin row187 详情弹框）。
 *
 * @author djs
 */
@Data
public class VegOutDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品名称。 */
    private String productName;

    /** 产品规格。 */
    private String productSpec;

    /**
     * 产品计量单位。
     *
     * <p>row194 起候选已扩到干货库 / 蛋类库，单位混杂（kg / 袋 / 桶 / 罐 / 枚）。
     * 详情页恒按 kg 展示会把「2 袋」印成「2.000kg」，故必须带出真实单位由前端按单位格式化。</p>
     */
    private String productUnit;

    /** 出库数量（按 {@link #productUnit} 计量；果蔬为 kg）。 */
    private BigDecimal outWeight;

    /** 出库单价（row193）：出库时录入的销售单价快照（t_warehouse_stock_flow.out_unit_price）。 */
    private BigDecimal outUnitPrice;

    /** 出库总价（row193）：出库量 × 出库单价。甲方原文写「出库单价*出库单价」是笔误。 */
    private BigDecimal outAmount;

    /** 地块编号（可空）。 */
    private String plotCode;
}
