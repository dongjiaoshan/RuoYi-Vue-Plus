package org.dromara.djs.plant.organic.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.djs.plant.organic.domain.CropOrganic;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 果蔬有机证书视图对象（PLT-MD-003）。
 *
 * <p>{@code cropName} 由 service 层 N+1 取（V1 数据量小，V2 改 LEFT JOIN）。</p>
 *
 * @author djs
 * @since PLT-MD-003
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = CropOrganic.class)
public class CropOrganicVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "证书编号")
    private String cropCertNo;

    @ExcelProperty(value = "颁发单位")
    private String cropCertCompany;

    @ExcelProperty(value = "有效期")
    private LocalDate cropCertValid;

    @ExcelProperty(value = "作物 ID")
    private Long cropId;

    /** 作物名（service 层 enrich）。 */
    @ExcelProperty(value = "作物名称")
    private String cropName;

    private String cropImagePreview;

    private String cropImageUrl;

    @ExcelProperty(value = "预警状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_yes_no")
    private Integer isWarning;

    @ExcelProperty(value = "创建时间")
    private Date createTime;
}
