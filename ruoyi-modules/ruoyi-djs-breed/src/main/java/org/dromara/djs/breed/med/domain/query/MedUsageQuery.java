package org.dromara.djs.breed.med.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDate;

/**
 * 药品领用列表查询入参（BRD-MED-002）。
 *
 * @author djs
 * @since BRD-MED-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MedUsageQuery extends BaseEntity {

    /**
     * 药品 ID（精确匹配）。
     */
    private Long medicineId;

    /**
     * 批次 ID（精确匹配）。
     */
    private Long batchId;

    /**
     * 类型：use / return / loss（精确匹配）。
     */
    private String usageType;

    /**
     * 业务日期下界（含）。
     */
    private LocalDate useDateFrom;

    /**
     * 业务日期上界（含）。
     */
    private LocalDate useDateTo;

    /**
     * 关联猪只（精确）。
     */
    private Long pigId;

}
