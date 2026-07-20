package org.dromara.djs.warehouse.location.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.util.List;

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
     * 库位类型（字典 djs_location_type；多选时走 {@link #locationTypes}）。
     */
    private String locationType;

    /**
     * 库位类型多选（字典 djs_location_type，IN 匹配）。
     */
    private List<String> locationTypes;

    /**
     * 状态（1 启用 / 2 停用）。
     */
    private Integer locationStatus;

}
