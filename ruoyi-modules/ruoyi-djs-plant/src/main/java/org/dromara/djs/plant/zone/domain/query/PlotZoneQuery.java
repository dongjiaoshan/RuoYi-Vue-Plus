package org.dromara.djs.plant.zone.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 片区查询参数（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlotZoneQuery extends BaseEntity {

    /**
     * 片区编码（精确匹配）。
     */
    private String zoneCode;

    /**
     * 片区名称（模糊匹配）。
     */
    private String zoneName;

    /**
     * 状态（1 正常 / 2 停用）。
     */
    private Integer zoneStatus;
}
