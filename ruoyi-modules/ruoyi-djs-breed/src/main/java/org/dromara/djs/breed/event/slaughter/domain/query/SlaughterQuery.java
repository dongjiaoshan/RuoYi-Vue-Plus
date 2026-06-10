package org.dromara.djs.breed.event.slaughter.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 出栏记录查询入参。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
public class SlaughterQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long pigId;
    private String earNo;
    private String outDest;
    private LocalDate beginDate;
    private LocalDate endDate;
}
