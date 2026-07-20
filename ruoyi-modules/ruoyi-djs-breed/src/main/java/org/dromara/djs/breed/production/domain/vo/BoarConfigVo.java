package org.dromara.djs.breed.production.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.production.domain.BoarConfig;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 精液 / 公猪配置出参 VO（BRD-MD-003 Tab2）。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BoarConfig.class)
public class BoarConfigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "公猪ID")
    private Long boarId;

    @ExcelProperty(value = "精液密度阈值")
    private BigDecimal spermQualityThreshold;

    @ExcelProperty(value = "采精间隔天数")
    private Integer breedingIntervalDays;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
