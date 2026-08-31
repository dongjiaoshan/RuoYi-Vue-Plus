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

    /** 出库果蔬品类数（该单去重产品数）。 */
    @ExcelProperty(value = "出库果蔬品类数")
    private Integer productKinds;

    /**
     * 出库果蔬重量合计（kg）。
     *
     * <p>表头带 (kg)：该合计<b>只累加 kg 行</b>（见 {@code VegOutMapper} 口径说明），
     * 干货 / 蛋类的袋 / 桶 / 罐 / 枚不进这个数，不写单位会被误读成「全部产品总量」。</p>
     */
    @ExcelProperty(value = "出库果蔬重量(kg)")
    private BigDecimal totalWeight;

    /** 出库金额合计（row192）：Σ 出库量 × 出库销售单价快照。单价为空的行按 0 计。 */
    @ExcelProperty(value = "出库金额(元)")
    private BigDecimal totalAmount;

    /** 出库操作人 id（内部主键，不导出）。 */
    private Long operatorId;

    /** 出库操作人姓名（service 反查 sys_user.nick_name 回填）。 */
    @ExcelProperty(value = "出库操作人")
    private String operatorName;
}
