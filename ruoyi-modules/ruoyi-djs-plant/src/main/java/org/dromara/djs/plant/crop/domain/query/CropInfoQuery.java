package org.dromara.djs.plant.crop.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 作物查询参数（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CropInfoQuery extends BaseEntity {

    /** 作物编码（精确）。 */
    private String cropCode;

    /** 作物名称（模糊）。 */
    private String cropName;

    /** 品种名（模糊）。 */
    private String varietyName;

    /** 品种来源/供应商（模糊）。 */
    private String varietyOrigin;

    /** 科属（djs_crop_family）。 */
    private String cropFamily;

    /** 作物属（自由文本，模糊）。 */
    private String cropGenus;

    /** 种植季节（包含匹配，逗号字段中含某季）。 */
    private String plantingSeason;

    /** 有机证书（1=有 / 2=无，按 t_plant_crop_organic_rel EXISTS 过滤）。 */
    private Integer hasOrganic;

    /** 证书是否预警（1=预警 / 2=正常，按关联证书 is_warning 过滤）。 */
    private Integer organicWarning;

    /** 更新人 ID（精确，对齐原型「更新人员」筛选）。 */
    private Long updateBy;
}
