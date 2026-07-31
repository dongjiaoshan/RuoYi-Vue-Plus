package org.dromara.djs.warehouse.product.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.format.DateTimeFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 外购商品详情「采购记录」子表导出 VO（admin row168）。
 *
 * <p>列与弹框内表格一一对应、同顺序，导出的是当前采购日期区间筛选下的全量流水
 * （数据源同 {@link ProductFlowRecordVo}，即 {@code t_warehouse_stock_flow}）。</p>
 *
 * <p>页面上由前端算出的展示值在此预先算好成文本列，避免 xlsx 里出现裸码 / 裸小数：</p>
 * <ul>
 *   <li>业务类型：{@code in_stock / pick_out / backend_out} 无字典，后端直接落中文
 *       （与前端 {@code product.flow.type*} 文案一致）；</li>
 *   <li>采购量：KG 单位保留 3 位小数、非 KG 计数单位取整（与前端 {@code formatStockQtyByUnit} 同口径）。</li>
 * </ul>
 *
 * @author djs
 * @since admin row168
 */
@Data
@ExcelIgnoreUnannotated
public class ProductFlowRecordExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 采购日期。 */
    @ExcelProperty(value = "采购日期")
    @DateTimeFormat("yyyy-MM-dd")
    private Date bizDate;

    /** 业务类型中文（入库 / 领用出库 / 后台出库）。 */
    @ExcelProperty(value = "业务类型")
    private String bizTypeLabel;

    /** 采购量（按单位口径格式化后的文本；KG 3 位小数，非 KG 整数）。 */
    @ExcelProperty(value = "采购量")
    private String bizNumLabel;

    /** 单位。 */
    @ExcelProperty(value = "单位")
    private String bizUnit;

    /** 供应商名称（无则空）。 */
    @ExcelProperty(value = "供应商")
    private String supplierName;

    /** 操作人名称。 */
    @ExcelProperty(value = "操作人")
    private String operatorName;

}
