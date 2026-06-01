package org.dromara.djs.warehouse.demand.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * mp 调度首页 KPI 出参（WMS-DEMAND-002）。
 *
 * <p>专供 {@code GET /djs/applet/warehouse/demand/home} 返回——mp 调度员首页顶部入口卡数字
 * + 中部今日 KPI。所有计数为当日（{@code demand_date = CURDATE()}）口径。</p>
 *
 * @author djs
 * @since WMS-DEMAND-002
 */
@Data
public class DispatchHomeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 白条入口卡：今日白条待确认单数（status=SUBMITTED 且 product_type=white_bar）。 */
    private Integer whiteBarPending;

    /** 白条入口卡：今日白条已确认单数（status IN CONFIRMED/IN_PRODUCTION/PARTIAL_SHIPPED/COMPLETED）。 */
    private Integer whiteBarConfirmed;

    /** 蔬菜入口卡：今日蔬菜待确认单数（status=SUBMITTED 且 product_type=vegetable）。 */
    private Integer vegetablePending;

    /** 蔬菜入口卡：今日蔬菜已确认单数。 */
    private Integer vegetableConfirmed;

    /** 今日 KPI：已确认单数（全业态 status IN CONFIRMED+）。 */
    private Integer confirmedCount;

    /** 今日 KPI：待确认单数（全业态 status=SUBMITTED）。 */
    private Integer pendingCount;

    /** 今日 KPI：待发货单数（全业态 status IN CONFIRMED/IN_PRODUCTION/PARTIAL_SHIPPED）。 */
    private Integer toShipCount;
}
