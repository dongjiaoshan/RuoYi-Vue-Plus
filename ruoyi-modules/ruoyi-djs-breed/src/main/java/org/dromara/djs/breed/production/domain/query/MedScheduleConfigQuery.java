package org.dromara.djs.breed.production.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 药品 / 疫苗周期配置列表查询入参（BRD-MD-003 Tab3）。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MedScheduleConfigQuery extends BaseEntity {

    /**
     * 药品类型（精确）。
     */
    private String medType;

    /**
     * 触发时机（精确）。
     */
    private String eventTrigger;

}
