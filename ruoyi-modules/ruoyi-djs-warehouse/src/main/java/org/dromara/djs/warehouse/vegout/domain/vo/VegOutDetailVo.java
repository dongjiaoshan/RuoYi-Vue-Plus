package org.dromara.djs.warehouse.vegout.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 毛菜间出库单明细行（admin row187 详情弹框）。
 *
 * <p>同时是 V6 row30「明细里增加导出功能」的导出模型：{@link ExcelIgnoreUnannotated} 下只导出
 * 标了 {@link ExcelProperty} 的列，列集合与详情弹框表头逐列一致
 * （产品名称 / 规格 / 出库量 / 出库单价 / 出库总价）—— {@code plotCode} 已按 row193 从页面撤掉，
 * {@code productUnit} 折进「出库量」一列（见 {@link #outQtyLabel}），两者都不单独成列。</p>
 *
 * @author djs
 */
@Data
@ExcelIgnoreUnannotated
public class VegOutDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品名称。 */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /** 产品规格。 */
    @ExcelProperty(value = "规格")
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

    /**
     * 出库量的带单位展示串（导出专用列，如 {@code 12.000kg} / {@code 3 袋}）。
     *
     * <p>不导出裸 {@link #outWeight}：row194 起候选扩到干货库 / 蛋类库，单位混杂
     * （kg / 袋 / 桶 / 罐 / 枚），xlsx 里出一列没有单位的数字会让「3 袋」和「3kg」无法分辨，
     * 且这一列本来就不该跨行求和。口径与弹框 {@code fmtQty} 一致：kg（或单位缺失）走 3 位小数 + kg，
     * 其余按「数值 空格 单位」。</p>
     *
     * <p>仅在导出路径由 service 派生填充；JSON 明细接口不填（前端自己按 {@link #productUnit} 格式化），
     * 保持既有响应不变。</p>
     */
    @ExcelProperty(value = "出库量")
    private String outQtyLabel;

    /** 出库单价（row193）：出库时录入的销售单价快照（t_warehouse_stock_flow.out_unit_price）。 */
    @ExcelProperty(value = "出库单价(元)")
    private BigDecimal outUnitPrice;

    /** 出库总价（row193）：出库量 × 出库单价。甲方原文写「出库单价*出库单价」是笔误。 */
    @ExcelProperty(value = "出库总价(元)")
    private BigDecimal outAmount;

    /** 地块编号（可空；row193 起页面已不显示，导出同步不出这一列）。 */
    private String plotCode;
}
