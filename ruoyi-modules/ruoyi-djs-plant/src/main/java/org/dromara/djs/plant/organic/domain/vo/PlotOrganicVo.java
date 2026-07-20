package org.dromara.djs.plant.organic.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.plant.organic.domain.PlotOrganic;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 土地有机证书视图对象（PLT-MD-003 / FIX-PLT-AD-INFO-LIST-001）。
 *
 * <p>{@code relatedPlots} 由 service 层根据 {@code t_plant_organic_plotno} 反查地块名 enrich；
 * {@code plotCount} = relatedPlots 数量（列表「覆盖地块数」计数列，service 层同步回填）。</p>
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

    /** 更新时间。 */
    @ExcelProperty(value = "更新时间")
    private Date updateTime;

    /** 更新人 ID（{@code sys_user.user_id}），供 {@link Translation} 反射取数翻译成 updateByName。 */
    private Long updateBy;

    /** 更新人姓名（注解翻译，VO 序列化时填）。 */
    @ExcelProperty(value = "更新人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "updateBy")
    private String updateByName;

    /** 关联地块（service 层 enrich）。 */
    private List<PlotRefVo> relatedPlots;

    /** 覆盖地块数（service 层 = relatedPlots.size()，列表计数列直接取）。 */
    @ExcelProperty(value = "覆盖地块数")
    private Integer plotCount;
}
