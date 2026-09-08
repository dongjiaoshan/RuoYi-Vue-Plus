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
 * （产品名称 / 规格 / 耳号 / 地块 / 出库量 / 出库单价 / 出库总价）——
 * {@code productUnit} 折进「出库量」一列（见 {@link #outQtyLabel}），不单独成列。</p>
 *
 * <p><b>字段声明序 = Excel 列序</b>（FastExcel 按字段扫描）：{@link #earNo} / {@link #plotLabel}
 * 必须声明在「规格」与「出库量」之间，才与 row191 要求的页面列位一致。</p>
 *
 * @author djs
 */
@Data
@ExcelIgnoreUnannotated
public class VegOutDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品业务编号（{@code t_warehouse_product_info.product_id}）。
     *
     * <p>只给「重新打印」做合并键用（V6 row108：同一产品的多条流水合成一行打印），
     * 页面表格与导出都不出这一列，故不标 {@link ExcelProperty}。</p>
     */
    private String productCode;

    /** 产品名称。 */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /** 产品规格。 */
    @ExcelProperty(value = "规格")
    private String productSpec;

    /**
     * 耳号（row191）：这条流水的 {@code t_warehouse_stock_flow.ear_no}。
     *
     * <p>猪肉来源的行才有；果蔬 / 干货 / 蛋类行为空，页面与导出都显示 {@code -}。</p>
     */
    @ExcelProperty(value = "耳号")
    private String earNo;

    /**
     * 地块名称（row199）：这条流水 {@code plot_id} 联出的 {@code t_plant_plot_info.plot_name}。
     *
     * <p>甲方口径「明细里的地块与出库时显示的保持一致」——新增出库抽屉显示的是地块<b>名称</b>
     * （{@code A1东9号}），不是编码（{@code A-A1东-0-009}）。接口出参给页面用，
     * 页面再交 {@code plotTag.ts#formatPlotLabel} 渲染；导出走 {@link #plotLabel}。</p>
     */
    private String plotName;

    /**
     * 【三期】标识（0=否 / 1=是；V6 row92）：这条流水的 {@code third_phase}。
     *
     * <p>三期作物不纳入地块管理、没有真实 {@code plot_id}，只靠这个标识在「地块」列显示「三期」。
     * 接口出参，页面 {@code formatPlotLabel} 用它决定渲染成什么；导出不单列裸 0/1，
     * 语义已并进 {@link #plotLabel}。</p>
     */
    private Integer thirdPhase;

    /**
     * 导出件的「地块」列（row191 列位 / row199 口径）。
     *
     * <p>由 service 用 {@code PlotLabel#of} 回填，规则与页面
     * {@code plus-ui/src/utils/plotTag.ts#formatPlotLabel} <b>完全相同</b>：
     * 三期 → 「三期」/ 有真实地块 → 地块名 / 都没有 → {@code -}。
     * 与库存查询 / 入出库记录三页共用同一口径，甲方拿导出对账不会看到第二种写法。</p>
     */
    @ExcelProperty(value = "地块")
    private String plotLabel;

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

}
