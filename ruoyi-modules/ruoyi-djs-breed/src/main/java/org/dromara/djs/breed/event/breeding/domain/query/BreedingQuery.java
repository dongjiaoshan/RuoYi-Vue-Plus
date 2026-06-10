package org.dromara.djs.breed.event.breeding.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 配种记录查询入参。
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
public class BreedingQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long pigId;
    private String earNo;
    private String breedingType;
    private String boarEarNo;
    private LocalDate beginDate;
    private LocalDate endDate;
}
