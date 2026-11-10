package org.dromara.djs.warehouse.demand.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/** 已确认需求汇总打印数据。 */
@Data
public class DemandPrintSummaryVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 所选需求日期。 */
    private LocalDate demandDate;
    /** 每个产品名称一行，不受普通列表分页影响。 */
    private List<DemandPrintSummaryItemVo> items;
}
