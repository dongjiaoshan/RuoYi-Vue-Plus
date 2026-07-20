package org.dromara.djs.breed.event.transfer.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 转移记录查询入参。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
public class TransferQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long pigId;
    private String earNo;
    private Long oldBarnId;
    private Long newBarnId;
    private LocalDate beginDate;
    private LocalDate endDate;
}
