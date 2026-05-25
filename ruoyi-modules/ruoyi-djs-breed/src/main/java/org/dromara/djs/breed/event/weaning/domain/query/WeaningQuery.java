package org.dromara.djs.breed.event.weaning.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class WeaningQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long pigId;
    private String earNo;
    private Long farrowId;
    private LocalDateTime beginDate;
    private LocalDateTime endDate;
}
