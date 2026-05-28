package org.dromara.djs.plant.organic.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.djs.plant.organic.domain.PlotOrganic;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 土地有机证书视图对象（PLT-MD-003）。
 *
 * <p>{@code relatedPlots} 由 service 层根据 {@code t_plant_organic_plotno} 反查地块名 enrich。</p>
 *
 * @author djs
 * @since PLT-MD-003
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PlotOrganic.class)
public class PlotOrganicVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "证书编号")
    private String organicNo;

    @ExcelProperty(value = "颁发单位")
    private String organicCompany;

    @ExcelProperty(value = "有效期")
    private LocalDate organicValid;

    private String organicImagePreview;

    private String organicImageUrl;

    @ExcelProperty(value = "预警状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_yes_no")
    private Integer isWarning;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /** 关联地块（service 层 enrich）。 */
    private List<PlotRefVo> relatedPlots;
}
