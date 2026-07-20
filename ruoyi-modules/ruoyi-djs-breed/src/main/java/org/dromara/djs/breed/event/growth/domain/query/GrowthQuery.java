package org.dromara.djs.breed.event.growth.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 生长记录查询入参（BRD-EVENT-005）。
 *
 * @author djs
 * @since BRD-EVENT-005
 */
@Data
public class GrowthQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long pigId;
    private String earNo;
    private LocalDate beginDate;
    private LocalDate endDate;
}
