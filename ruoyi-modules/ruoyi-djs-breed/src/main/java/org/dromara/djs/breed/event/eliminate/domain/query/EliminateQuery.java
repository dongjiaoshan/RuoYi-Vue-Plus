package org.dromara.djs.breed.event.eliminate.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

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
    private LocalDateTime beginDate;
    private LocalDateTime endDate;
}
