package org.dromara.djs.breed.event.intro.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 引种记录查询入参（BRD-EVENT-001 admin 只读列表）。
 *
 * @author djs
 * @since BRD-EVENT-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PigIntroQuery extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 引种单号（前缀模糊匹配，如 INT2026）。 */
    private String introduceNo;

    /** 引种方式（external / internal）。 */
    private String introduceType;

    /** 供应商 ID。 */
    private Long supplierId;

    /** 性别（F/M）。 */
    private String pigSex;

    /** 引种日期范围下限（含）。 */
    private LocalDate beginDate;

    /** 引种日期范围上限（含）。 */
    private LocalDate endDate;
}
