package org.dromara.djs.breed.event.nullreturn.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

@Data
public class NullReturnQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long pigId;
    private String earNo;
    /** DB 单字符码 R/A/N（查询时传 DB 字段值，与 BO 不同）。 */
    private String abnormalType;
    private LocalDate beginDate;
    private LocalDate endDate;
}
