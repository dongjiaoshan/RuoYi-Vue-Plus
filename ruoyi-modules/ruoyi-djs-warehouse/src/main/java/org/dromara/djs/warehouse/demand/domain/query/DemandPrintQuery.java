package org.dromara.djs.warehouse.demand.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 需求单打印列表查询参数（V7-R8）。
 *
 * <p>需求日期区间默认值（今日 ~ 明日）由前端给，后端不兜默认：两端都兜就会出现「两个今日」
 * （浏览器时区 vs 服务器时区）。区间两端都可空 = 不按日期过滤。</p>
 *
 * @author djs
 * @since V7-R8
 */
@Data
public class DemandPrintQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 需求日期起（含）。 */
    private LocalDate beginDate;

    /** 需求日期止（含）。 */
    private LocalDate endDate;

    /** 需求门店多选（空 = 全部门店）。 */
    private List<Long> storeIds;
}
