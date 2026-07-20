package org.dromara.djs.breed.event.eliminate.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 淘汰记录查询入参。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
public class EliminateQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long pigId;
    private String earNo;
    private String cullingReason;
    private LocalDate beginDate;
    private LocalDate endDate;
}
