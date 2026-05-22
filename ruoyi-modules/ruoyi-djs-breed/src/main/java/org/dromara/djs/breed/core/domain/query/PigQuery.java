package org.dromara.djs.breed.core.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 猪只列表查询入参（BRD-CORE-001）。
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PigQuery extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 耳号（精确匹配）。 */
    private String earNo;

    /** 性别（F/M）。 */
    private String pigSex;

    /** 类型（sow/boar/piglet/fattening）。 */
    private String pigType;

    /** 当前状态（10 lifecycle，精确）。 */
    private String currentStatus;

    /** 栋舍 ID。 */
    private Long barnId;

    /** 栏位 ID。 */
    private Long penId;

    /** 是否排除终态（true=不返 END 行）。 */
    private Boolean excludeEnd;
}
