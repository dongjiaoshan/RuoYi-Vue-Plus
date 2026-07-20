package org.dromara.djs.breed.production.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.production.domain.ProductionCycleConfig;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 生产周期配置出参 VO（BRD-MD-003 Tab1）。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProductionCycleConfig.class)
public class ProductionCycleConfigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "业务键")
    private String configKey;

    @ExcelProperty(value = "默认值")
    private Integer defaultValue;

    @ExcelProperty(value = "自定义值")
    private Integer customValue;

    @ExcelProperty(value = "单位")
    private String unit;

    @ExcelProperty(value = "说明")
    private String description;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
