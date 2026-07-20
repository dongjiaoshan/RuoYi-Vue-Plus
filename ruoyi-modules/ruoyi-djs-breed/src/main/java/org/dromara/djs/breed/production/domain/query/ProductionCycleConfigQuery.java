package org.dromara.djs.breed.production.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 生产周期配置列表查询入参（BRD-MD-003 Tab1）。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductionCycleConfigQuery extends BaseEntity {

    /**
     * 业务键（精确匹配）。
     */
    private String configKey;

    /**
     * 说明（模糊匹配）。
     */
    private String description;

}
