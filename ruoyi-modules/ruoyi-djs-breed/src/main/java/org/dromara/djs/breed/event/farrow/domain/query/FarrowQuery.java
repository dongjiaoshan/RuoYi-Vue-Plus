package org.dromara.djs.breed.event.farrow.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 分娩查询入参（admin 列表分页 / mp 端 picker 共用基础结构）。
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
public class FarrowQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long pigId;
    private String earNo;
    private LocalDate beginDate;
    private LocalDate endDate;
}
