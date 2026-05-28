package org.dromara.djs.plant.organic.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 土地有机证书查询参数（PLT-MD-003）。
 *
 * @author djs
 * @since PLT-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlotOrganicQuery extends BaseEntity {

    /** 证书编号（模糊）。 */
    private String organicNo;

    /** 颁发单位（模糊）。 */
    private String organicCompany;

    /** 预警状态（1=预警 / 2=正常）。 */
    private Integer isWarning;
}
