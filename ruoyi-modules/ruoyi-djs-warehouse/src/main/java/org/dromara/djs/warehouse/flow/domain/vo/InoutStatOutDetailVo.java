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
 * 出库统计「查看详情」明细行（V6-R186）：某个汇总行在日期区间内的逐条出库流水。
 *
 * <p>列序 = 甲方 row186 第 3 点原文序：出库日期 / 产品编码 / 产品名称 / 规格 / 出库量 /
 * 出库去向 / 出库记录人 / 出库操作时间。导出与弹窗表格逐列一致（第 4 点）。</p>
 *
 * <p>单位折进出库量一列的理由同 {@link InoutStatInDetailVo}。</p>
 *
 * @author djs
 * @since V6-R186
 */
@Data
@ExcelIgnoreUnannotated
public class InoutStatOutDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 出库日期（{@code flow_date} 的业务日期部分）。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @ExcelProperty(value = "出库日期")
    private Date flowDate;

    /** 产品编码（{@code product_info.product_id} 业务码）。 */
    @ExcelProperty(value = "产品编码")
    private String productCode;

    /** 产品名称。 */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /** 规格（空时 service 兜 "-"）。 */
    @ExcelProperty(value = "规格")
    private String productSpec;

    /** 出库量的带单位展示串（口径见 {@link InoutStatInDetailVo#getInQtyLabel()}）。 */
    @ExcelProperty(value = "出库量")
    private String outQtyLabel;

    /** 出库去向（djs_stock_out_dest 翻译后；空 / 字典未命中兜「未指定」，与汇总行同一套兜底）。 */
    @ExcelProperty(value = "出库去向")
    private String outDestName;

    /** 出库记录人（SQL LEFT JOIN {@code sys_user.nick_name}；查不到时 service 兜 "-"）。 */
    @ExcelProperty(value = "出库记录人")
    private String operatorName;

    /** 出库操作时间（流水落库时间 {@code create_time}，不是业务日期）。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "出库操作时间")
    private Date createTime;

    /** 出库量原始值（mapper 出，service 拼 {@link #outQtyLabel} 用；JSON 保留，不导出）。 */
    private BigDecimal outboundQty;

    /** 产品单位原始值（mapper 出，service 拼 {@link #outQtyLabel} 用；JSON 保留，不导出）。 */
    private String productUnit;

    /** 出库去向原始值（mapper 出，service 翻译用，不导出）。 */
    private String stockOutDest;
}
