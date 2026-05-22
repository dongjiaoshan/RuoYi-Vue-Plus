package org.dromara.djs.breed.core.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.core.domain.Pig;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 猪只列表 / 详情出参（BRD-CORE-001）。
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = Pig.class)
public class PigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "耳号简版")
    private String earNo;

    @ExcelProperty(value = "耳号全版")
    private String earTag;

    @ExcelProperty(value = "生命周期")
    private Integer lifecycleId;

    @ExcelProperty(value = "可回收")
    private Integer recyclable;

    @ExcelProperty(value = "性别")
    private String pigSex;

    @ExcelProperty(value = "类型")
    private String pigType;

    @ExcelProperty(value = "品种")
    private String pigBreedCode;

    @ExcelProperty(value = "品系")
    private String pigStrainCode;

    @ExcelProperty(value = "当前状态")
    private String currentStatus;

    @ExcelProperty(value = "进入状态时间")
    private LocalDateTime statusStartedAt;

    @ExcelProperty(value = "终止原因")
    private String endReason;

    @ExcelProperty(value = "出生日期")
    private LocalDate birthDate;

    @ExcelProperty(value = "引种日期")
    private LocalDate introduceDate;

    @ExcelProperty(value = "胎次")
    private Integer parity;

    @ExcelProperty(value = "栋舍ID")
    private Long barnId;

    @ExcelProperty(value = "栏位ID")
    private Long penId;

    @ExcelProperty(value = "最近配种ID")
    private Long matingId;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

    private Integer version;
}
