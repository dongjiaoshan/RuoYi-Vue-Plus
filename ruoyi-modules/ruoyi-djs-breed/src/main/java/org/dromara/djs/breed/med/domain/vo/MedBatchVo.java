package org.dromara.djs.breed.med.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.med.domain.MedBatch;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 药品批次视图对象（BRD-MED-001）。
 *
 * @author djs
 * @since BRD-MED-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MedBatch.class)
public class MedBatchVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 批次 ID。
     */
    @ExcelProperty(value = "ID")
    private Long id;

    /**
     * 药品 ID。
     */
    @ExcelProperty(value = "药品ID")
    private Long medicineId;

    /**
     * 批次编码。
     */
    @ExcelProperty(value = "批次编码")
    private String batchNo;

    /**
     * 生产日期。
     */
    @ExcelProperty(value = "生产日期")
    private LocalDate productionDate;

    /**
     * 过期日期。
     */
    @ExcelProperty(value = "过期日期")
    private LocalDate expiryDate;

    /**
     * 批次剩余库存。
     */
    @ExcelProperty(value = "库存")
    private BigDecimal quantity;

    /**
     * 进货单价。
     */
    @ExcelProperty(value = "单价")
    private BigDecimal unitPrice;

    /**
     * 备注。
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 创建时间。
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
