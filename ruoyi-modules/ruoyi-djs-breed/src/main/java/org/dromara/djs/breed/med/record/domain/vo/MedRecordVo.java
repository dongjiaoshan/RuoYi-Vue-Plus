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

    /** 用药时猪只类型快照（djs_pig_type），列表耳号后展示 + 按类型搜索。 */
    @ExcelProperty(value = "猪只类型")
    private String pigType;

    /** 用药时母猪状态快照（djs_pig_lifecycle）。 */
    @ExcelProperty(value = "猪只状态")
    private String pigStatus;

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

    /** enrich：批次编码（替代裸 batchId）。 */
    @ExcelProperty(value = "批次编码")
    private String batchNo;

    private Long usageId;

    private Long scheduleId;

    @ExcelProperty(value = "用药剂量")
    private BigDecimal medicineDosage;

    /** 剂量单位（记录/展示用）。 */
    @ExcelProperty(value = "剂量单位")
    private String dosageUnit;

    @ExcelProperty(value = "操作人ID")
    private Long operatorId;

    @ExcelProperty(value = "操作人")
    private String operatorName;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
