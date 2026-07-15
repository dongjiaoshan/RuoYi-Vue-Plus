package org.dromara.djs.breed.med.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.djs.breed.med.domain.Medicine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 药品库视图对象（BRD-MED-001）。
 *
 * @author djs
 * @since BRD-MED-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = Medicine.class)
public class MedicineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 药品 ID。
     */
    @ExcelProperty(value = "ID")
    private Long id;

    /**
     * 药品编码。
     */
    @ExcelProperty(value = "药品编码")
    private String medicineCode;

    /**
     * 药品名称。
     */
    @ExcelProperty(value = "药品名称")
    private String medicineName;

    /**
     * 药品类型（字典 djs_med_type）。
     */
    @ExcelProperty(value = "类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_med_type")
    private String medicineType;

    /**
     * 供应商 ID。
     */
    @ExcelProperty(value = "供应商ID")
    private Long supplierId;

    /**
     * 批准文号。
     */
    @ExcelProperty(value = "批准文号")
    private String approvalNo;

    /**
     * 批号。
     */
    @ExcelProperty(value = "批号")
    private String batchNo;

    /**
     * 过期日期。
     */
    @ExcelProperty(value = "过期日期")
    private LocalDate expireDate;

    /**
     * 休药期（天）。
     */
    @ExcelProperty(value = "休药期（天）")
    private Integer withdrawDays;

    /**
     * 单位。
     */
    @ExcelProperty(value = "单位")
    private String unit;

    /**
     * 当前库存。
     */
    @ExcelProperty(value = "当前库存")
    private BigDecimal currentStock;

    /**
     * 规格。
     */
    @ExcelProperty(value = "规格")
    private String spec;

    /**
     * 展示图 public URL（IMG-LIB-001 resolver 回填，mp 领用卡片展示；不入 Excel）。
     */
    private String imageUrl;

    /**
     * 今日已领（当日仓库领用出库 SUM，全场口径，与物资领用卡同源；mp 药品领用卡下排，不入 Excel）。row129。
     */
    private BigDecimal todayPicked;

    /**
     * 今日退回（当日仓库领用退回入库 SUM，全场口径；不入 Excel）。row129。
     */
    private BigDecimal todayReturned;

    /**
     * 今日损耗（当日仓库损耗出库 SUM，全场口径；不入 Excel）。row129。
     */
    private BigDecimal todayLoss;

    /**
     * 生产厂家。
     */
    @ExcelProperty(value = "生产厂家")
    private String manufacturer;

    /**
     * 储存条件。
     */
    @ExcelProperty(value = "储存条件")
    private String storageCondition;

    /**
     * 状态（1 启用 / 0 停用）。
     */
    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "sys_normal_disable")
    private Integer medStatus;

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
