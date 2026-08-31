package org.dromara.djs.breed.production.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.djs.common.excel.DictOrRawConvert;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.production.domain.MedScheduleConfig;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 药品 / 疫苗周期配置出参 VO（BRD-MD-003 Tab3）。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MedScheduleConfig.class)
public class MedScheduleConfigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "药品类型", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_med_type")
    private String medType;

    @ExcelProperty(value = "触发时机")
    private String eventTrigger;

    @ExcelProperty(value = "天数偏移")
    private Integer daysOffset;

    @ExcelProperty(value = "说明")
    private String description;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
