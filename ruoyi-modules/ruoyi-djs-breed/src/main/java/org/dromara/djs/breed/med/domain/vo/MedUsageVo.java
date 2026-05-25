package org.dromara.djs.breed.med.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.med.domain.MedUsage;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 药品领用视图对象（BRD-MED-002）。
 *
 * @author djs
 * @since BRD-MED-002
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MedUsage.class)
public class MedUsageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "批次ID")
    private Long batchId;

    @ExcelProperty(value = "药品ID")
    private Long medicineId;

    @ExcelProperty(value = "类型")
    private String usageType;

    @ExcelProperty(value = "数量")
    private BigDecimal usageQty;

    @ExcelProperty(value = "业务日期")
    private LocalDate useDate;

    @ExcelProperty(value = "关联栏位")
    private Long relatedPenId;

    @ExcelProperty(value = "关联猪只")
    private Long pigId;

    @ExcelProperty(value = "用药计划")
    private Long scheduleId;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
