package org.dromara.djs.plant.organic.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 果蔬有机证书查询参数（PLT-MD-003）。
 *
 * @author djs
 * @since PLT-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CropOrganicQuery extends BaseEntity {

    /** 证书编号（模糊）。 */
    private String cropCertNo;

    /** 颁发单位（模糊）。 */
    private String cropCertCompany;

    /** 关联作物 ID。 */
    private Long cropId;

    /** 预警状态（1=预警 / 2=正常）。 */
    private Integer isWarning;
}
