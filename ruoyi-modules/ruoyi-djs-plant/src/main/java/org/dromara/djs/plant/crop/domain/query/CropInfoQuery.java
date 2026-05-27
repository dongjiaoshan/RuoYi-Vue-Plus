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

    /** 科属（djs_crop_family）。 */
    private String cropFamily;

    /** 种植季节（包含匹配，逗号字段中含某季）。 */
    private String plantingSeason;
}
