package org.dromara.djs.plant.pick.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 采摘活动查询条件（PLT-PLAN-002）。
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Data
public class PickActivityQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String activityNo;
    private String activityName;
    private String activityStatus;
    private Long cropId;

    /** 活动日期范围（含两端）。 */
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
