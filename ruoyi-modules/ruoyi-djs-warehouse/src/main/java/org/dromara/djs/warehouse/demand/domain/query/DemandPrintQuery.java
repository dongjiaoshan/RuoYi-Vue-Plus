package org.dromara.djs.warehouse.demand.domain.query;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/** 需求单打印列表：按单一需求日期与门店筛选，默认日期由前端按本地时区提供。 */
@Data
public class DemandPrintQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 需求日期。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate demandDate;

    /** 需求门店多选（空 = 全部门店）。 */
    private List<Long> storeIds;
}
