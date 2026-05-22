package org.dromara.djs.breed.med.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 药品库列表查询入参（BRD-MED-001）。
 *
 * @author djs
 * @since BRD-MED-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MedicineQuery extends BaseEntity {

    /**
     * 药品名称（模糊匹配）。
     */
    private String medicineName;

    /**
     * 药品编码（精确匹配）。
     */
    private String medicineCode;

    /**
     * 药品类型（字典 djs_med_type）。
     */
    private String medicineType;

    /**
     * 供应商 ID。
     */
    private Long supplierId;

    /**
     * 药品状态（1 启用 / 0 停用）。
     */
    private Integer medStatus;

}
