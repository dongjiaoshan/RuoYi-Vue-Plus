package org.dromara.djs.breed.event.weaning.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

@Data
public class WeaningQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long pigId;
    private String earNo;
    private Long farrowId;
    private LocalDate beginDate;
    private LocalDate endDate;
}
