package org.dromara.djs.breed.event.eartag.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 仔猪耳标记录查询入参（BRD-EVENT-003 admin 只读列表）。
 *
 * @author djs
 * @since BRD-EVENT-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PigletEarTagQuery extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 仔猪耳号（精确匹配）。 */
    private String pigletEarNo;

    /** 母猪耳号（精确匹配）。 */
    private String motherEarNo;

    /** 分娩 ID。 */
    private Long farrowId;

    /** 仔猪性别（F/M）。 */
    private String pigletSex;

    /** 打标日期范围下限（含）。 */
    private LocalDate beginDate;

    /** 打标日期范围上限（含）。 */
    private LocalDate endDate;
}
