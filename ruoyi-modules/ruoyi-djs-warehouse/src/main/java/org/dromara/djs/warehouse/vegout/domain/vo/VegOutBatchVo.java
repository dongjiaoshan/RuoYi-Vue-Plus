package org.dromara.djs.warehouse.vegout.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.format.DateTimeFormat;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.djs.common.excel.DictOrRawConvert;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 毛菜间出库单（admin row187 列表行）：一次提交聚合成一行。
 *
 * <p>同时是 V6 row31「页面增加导出功能」的导出模型：{@link ExcelIgnoreUnannotated} 下只导出
 * 标了 {@link ExcelProperty} 的列，列集合与 admin 列表表头逐列一致（不含「操作」列），
 * {@code operatorId} 这类内部主键不进 xlsx。</p>
 *
 * @author djs
 */
@Data
@ExcelIgnoreUnannotated
public class VegOutBatchVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 批量出库单号（stock_flow.batch_no，详情按它查明细）。 */
    @ExcelProperty(value = "出库单号")
    private String batchNo;

    /**
     * 出库日期。
     *
     * <p>{@link DateTimeFormat} 只作用于 xlsx 写出（FastExcel），不影响 JSON —— 故意不加
     * {@code @JsonFormat}：接口返回格式保持原样（全局默认 {@code yyyy-MM-dd HH:mm:ss}），
     * 免得动到已在跑的列表 / 打印单。导出侧按页面显示的纯日期出，不带 00:00:00。</p>
     */
    @ExcelProperty(value = "出库日期")
    @DateTimeFormat("yyyy-MM-dd")
    private Date outDate;

    /** 出库去向（字典 djs_stock_out_dest）。 */
    @ExcelProperty(value = "出库去向", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_stock_out_dest")
    private String outDest;

    /** 出库品类数（该单去重产品数）。 */
    @ExcelProperty(value = "出库品类数")
    private Integer productKinds;

    /**
     * 出库重量合计（kg）。
     *
     * <p>表头带 (kg)：该合计<b>只累加 kg 行</b>（见 {@code VegOutMapper} 口径说明），
     * 干货 / 蛋类的袋 / 桶 / 罐 / 枚不进这个数，它们落在 {@link #totalQty}。</p>
     */
    @ExcelProperty(value = "出库重量(kg)")
    private BigDecimal totalWeight;

    /**
     * 出库量合计（V6 row220 新增列）：该单里<b>单位不是 kg</b> 的行的数量之和。
     *
     * <p>与 {@link #totalWeight} 是同一判据的两半，互不重叠。<b>故意不带单位</b>——
     * 一张单里可能同时有袋 / 桶 / 罐 / 枚，这一列是「非 kg 的货一共出了多少件」的粗汇总，
     * 单位混着加本就没有物理意义，要看逐行单位请进详情弹框（那里每行按自己的单位展示）。</p>
     */
    @ExcelProperty(value = "出库量")
    private BigDecimal totalQty;

    /** 出库金额合计（row192）：Σ 出库量 × 出库销售单价快照。单价为空的行按 0 计。 */
    @ExcelProperty(value = "出库金额(元)")
    private BigDecimal totalAmount;

    /** 出库操作人 id（内部主键，不导出）。 */
    private Long operatorId;

    /** 出库操作人姓名（service 反查 sys_user.nick_name 回填）。 */
    @ExcelProperty(value = "出库操作人")
    private String operatorName;
}
