package org.dromara.djs.breed.event.die.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 死亡记录查询入参。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
public class DieQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long pigId;
    private String earNo;
    private String deathKind;
    private String deathReason;
    private LocalDateTime beginDate;
    private LocalDateTime endDate;
}
