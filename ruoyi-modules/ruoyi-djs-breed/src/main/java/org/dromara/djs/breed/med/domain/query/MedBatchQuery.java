package org.dromara.djs.breed.med.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 药品批次列表查询入参（BRD-MED-001）。
 *
 * @author djs
 * @since BRD-MED-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MedBatchQuery extends BaseEntity {

    /**
     * 药品 ID（精确匹配，admin 列表通常按药品过滤）。
     */
    private Long medicineId;

    /**
     * 批次编码（模糊匹配）。
     */
    private String batchNo;

}
