package org.dromara.djs.warehouse.purchase.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 采购入库记录 VO（D9 hotfix admin 列表 / 导出用）。
 *
 * <p>本质就是 {@code t_warehouse_stock_flow} 的一行（{@code flow_type='purchase_in'}）。
 * 由 service 层批量 JOIN 回填 {@code productName / productUnit / locationName}，避免 N+1。</p>
 *
 * @author djs
 * @since D9 hotfix purchase-in
 */
@Data
@ExcelIgnoreUnannotated
public class PurchaseInRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "记录ID")
    private Long id;

    @ExcelProperty(value = "流水号")
    private String flowNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "入库时间")
    private Date flowDate;

    private Long productId;

    /**
     * 产品名（service 层 JOIN 回填）。
     */
    @ExcelProperty(value = "产品")
    private String productName;

    /**
     * 单位（service JOIN 回填）。
     */
    @ExcelProperty(value = "单位")
    private String productUnit;

    /**
     * 库位 ID（DDL 列名 {@code warehouse_id}，实为 location FK）。
     */
    private Long locationId;

    /**
     * 库位名（service 回填）。
     */
    @ExcelProperty(value = "库位")
    private String locationName;

    /**
     * 入库数量（始终为正，等于 {@code change_quantity}）。
     */
    @ExcelProperty(value = "入库数量")
    private BigDecimal changeQuantity;

    private Long operatorId;

    /**
     * 操作人姓名（ruoyi 翻译）。
     */
    @ExcelProperty(value = "操作人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    @ExcelProperty(value = "备注")
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
