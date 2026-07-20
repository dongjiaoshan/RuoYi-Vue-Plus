package org.dromara.djs.breed.farm.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 栋舍列表查询入参（BRD-MD-002）。
 *
 * @author djs
 * @since BRD-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BarnQuery extends BaseEntity {

    /**
     * 栋舍编码（精确匹配）。
     */
    private String barnCode;

    /**
     * 栋舍名称（模糊匹配）。
     */
    private String barnName;

    /**
     * 栋舍类型。
     */
    private String barnType;

    /**
     * 状态（1/0）。
     */
    private Integer barnStatus;

}
