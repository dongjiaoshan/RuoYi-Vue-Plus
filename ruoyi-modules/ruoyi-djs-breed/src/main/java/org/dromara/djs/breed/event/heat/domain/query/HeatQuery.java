package org.dromara.djs.breed.event.heat.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

@Data
public class HeatQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long pigId;
    private String earNo;
    private Integer isPregnantConfirmed;
    private LocalDate beginDate;
    private LocalDate endDate;
}
