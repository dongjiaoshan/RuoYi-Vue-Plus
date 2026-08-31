package org.dromara.djs.plant.organic.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.djs.common.excel.DictOrRawConvert;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.plant.organic.domain.CropOrganic;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 果蔬有机证书视图对象（PLT-MD-003 / FIX-PLT-AD-INFO-LIST-001）。
 *
 * <p>{@code relatedCrops} 由 service 层根据 {@code t_plant_crop_organic_rel} 反查作物名 enrich
 * （一证多作物）。旧单值 {@code cropId} / {@code cropName} 保留作过渡兼容，列表以 {@code relatedCrops} 为准。</p>
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

    /** 旧单值关联作物 ID（过渡保留，一证多作物以 relatedCrops 为准）。 */
    @ExcelProperty(value = "作物 ID")
    private Long cropId;

    /** 旧单值关联作物名（service 层 enrich，过渡保留）。 */
    @ExcelProperty(value = "作物名称")
    private String cropName;

    /** 关联作物（一证多作物，service 层 enrich）。 */
    private List<CropRefVo> relatedCrops;

    private String cropImagePreview;

    private String cropImageUrl;

    @ExcelProperty(value = "预警状态", converter = DictOrRawConvert.class)
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
}
