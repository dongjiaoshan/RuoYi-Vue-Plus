package org.dromara.djs.breed.med.record.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.med.record.domain.MedRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 用药治疗流水 VO（BRD-MED-003）。
 *
 * <p>字典 label 由前端 BizTable / DictPicker 翻译（沿用 BRD-MED-002 同模式，
 * 不在 VO 层耦合 ruoyi {@code @Translation}）。</p>
 *
 * @author djs
 * @since BRD-MED-003
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MedRecord.class)
public class MedRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "用药日期")
    private LocalDateTime useDate;

    @ExcelProperty(value = "猪只ID")
    private Long pigId;

    @ExcelProperty(value = "耳号")
    private String earNo;

    private Long masterId;

    @ExcelProperty(value = "记录类型(单/批)")
    private Integer drugType;

    @ExcelProperty(value = "用药类型")
    private String medicineType;

    @ExcelProperty(value = "用药原因")
    private String medicineReason;

    @ExcelProperty(value = "用药方式")
    private String medicineWay;

    @ExcelProperty(value = "药品ID")
    private Long medicineId;

    @ExcelProperty(value = "药品名称")
    private String medicineName;

    @ExcelProperty(value = "批次ID")
    private Long batchId;

    private Long usageId;

    private Long scheduleId;

    @ExcelProperty(value = "用药剂量")
    private BigDecimal medicineDosage;

    @ExcelProperty(value = "操作人ID")
    private Long operatorId;

    @ExcelProperty(value = "操作人")
    private String operatorName;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
