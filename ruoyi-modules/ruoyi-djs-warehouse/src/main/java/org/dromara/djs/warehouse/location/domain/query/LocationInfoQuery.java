package org.dromara.djs.warehouse.location.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 库位列表查询入参（WMS-MD-001）。
 *
 * @author djs
 * @since WMS-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LocationInfoQuery extends BaseEntity {

    /**
     * 库位编码（精确匹配）。
     */
    private String locationCode;

    /**
     * 库位名称（模糊匹配）。
     */
    private String locationName;

    /**
     * 库位类型（字典 djs_location_type）。
     */
    private String locationType;

    /**
     * 状态（1 启用 / 2 停用）。
     */
    private Integer locationStatus;

}
