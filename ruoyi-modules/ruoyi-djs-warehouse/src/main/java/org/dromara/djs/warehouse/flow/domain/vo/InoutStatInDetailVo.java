package org.dromara.djs.warehouse.flow.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 入库统计「查看详情」明细行（V6-R186）：某个汇总行在日期区间内的逐条入库流水。
 *
 * <p>列序 = 甲方 row186 第 2 点原文序：入库日期 / 产品编码 / 产品名称 / 规格 / 入库量 /
 * 入库供应商 / 入库记录人 / 入库操作时间。导出与弹窗表格逐列一致（第 4 点）。</p>
 *
 * <p>甲方没要「单位」列，但候选产品单位混杂（kg / 袋 / 桶 / 罐 / 枚），一列裸数字会让
 * 「3 袋」与「3kg」无法分辨，故把单位折进入库量一列（{@link #inQtyLabel}），
 * 由 service 单通道生成 —— 页面与 Excel 读同一个字段，必然一致。</p>
 *
 * @author djs
 * @since V6-R186
 */
@Data
@ExcelIgnoreUnannotated
public class InoutStatInDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 入库日期（{@code flow_date} 的业务日期部分）。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @ExcelProperty(value = "入库日期")
    private Date flowDate;

    /** 产品编码（{@code product_info.product_id} 业务码，如 P0001 / Y00099）。 */
    @ExcelProperty(value = "产品编码")
    private String productCode;

    /** 产品名称。 */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /** 规格（空时 service 兜 "-"）。 */
    @ExcelProperty(value = "规格")
    private String productSpec;

    /**
     * 入库量的带单位展示串（如 {@code 12.000kg} / {@code 3 袋}）。
     *
     * <p>页面与 Excel 都读这一个字段（单通道）。口径：kg / 公斤 / 单位缺失 → 3 位小数 + kg，
     * 其余按「数值 空格 单位」，与兄弟页「毛菜间出库明细」的 {@code outQtyLabel} 完全相同。</p>
     */
    @ExcelProperty(value = "入库量")
    private String inQtyLabel;

    /** 入库供应商（空时 service 兜「无供应商」，与汇总行同一套兜底）。 */
    @ExcelProperty(value = "入库供应商")
    private String supplierName;

    /** 入库记录人（SQL LEFT JOIN {@code sys_user.nick_name}；查不到时 service 兜 "-"）。 */
    @ExcelProperty(value = "入库记录人")
    private String operatorName;

    /** 入库操作时间（流水落库时间 {@code create_time}，不是业务日期）。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "入库操作时间")
    private Date createTime;

    /** 入库量原始值（mapper 出，service 拼 {@link #inQtyLabel} 用；JSON 保留，不导出）。 */
    private BigDecimal inboundQty;

    /** 产品单位原始值（mapper 出，service 拼 {@link #inQtyLabel} 用；JSON 保留，不导出）。 */
    private String productUnit;
}
