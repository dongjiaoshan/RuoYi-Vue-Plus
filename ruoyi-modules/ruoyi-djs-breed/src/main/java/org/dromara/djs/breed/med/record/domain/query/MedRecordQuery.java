package org.dromara.djs.breed.med.record.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDate;

/**
 * 用药治疗流水查询入参（BRD-MED-003）。
 *
 * @author djs
 * @since BRD-MED-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MedRecordQuery extends BaseEntity {

    /**
     * 猪只 ID（精确）。
     */
    private Long pigId;

    /**
     * 耳号（模糊）。
     */
    private String earNo;

    /**
     * 用药类型（djs_medicine_use_type，精确）。
     */
    private String medicineType;

    /**
     * 药品名称（模糊）。
     */
    private String medicineName;

    /**
     * 用药时猪只类型（djs_pig_type，精确，走 pig_type 快照列）。
     */
    private String pigType;

    /**
     * 记录类型（1=单只 / 2=master / 3=detail，精确）。
     */
    private Integer drugType;

    /**
     * 业务日期下界（含）。
     */
    private LocalDate beginDate;

    /**
     * 业务日期上界（含）。
     */
    private LocalDate endDate;

    /**
     * 批次 ID（精确）。
     */
    private Long batchId;

    /**
     * 药品 ID（精确）。
     */
    private Long medicineId;

    /**
     * 操作人 user_id（精确）。
     */
    private Long operatorId;

}
