package org.dromara.djs.breed.production.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.production.domain.FattenAgeStage;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 育肥日龄阶段配置出参 VO（A1 Tab2）。
 *
 * @author djs
 * @since A1
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = FattenAgeStage.class)
public class FattenAgeStageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "起始日龄")
    private Integer startAge;

    @ExcelProperty(value = "截止日龄")
    private Integer endAge;

    @ExcelProperty(value = "是否记录生长记录")
    private Integer recordGrowth;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
