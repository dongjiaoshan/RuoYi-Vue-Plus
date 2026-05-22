package org.dromara.djs.breed.farm.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 栏位列表查询入参（BRD-MD-002）。
 *
 * @author djs
 * @since BRD-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PenQuery extends BaseEntity {

    /**
     * 所属栋舍 ID（前端树切栋舍后必传，否则全表扫）。
     */
    private Long barnId;

    /**
     * 栏位编码（精确）。
     */
    private String penCode;

    /**
     * 栏位名称（模糊）。
     */
    private String penName;

    /**
     * 栏位类型。
     */
    private String penType;

    /**
     * 状态。
     */
    private Integer penStatus;

}
