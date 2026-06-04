package org.dromara.djs.warehouse.demand.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 调度统计页聚合出参（WMS-DEMAND-002）。
 *
 * <p>专供 {@code GET /djs/applet/warehouse/demand/stats/summary?date=} 返回——mp 调度统计页
 * 5 个 dashboard 卡片（车间效能 / 进出库 / 损耗 / 退货 / 发货）。V1 简化为静态 KPI 数字，
 * 数据来源聚合 demand + stock_flow + return_product + shipment 当日口径。</p>
 *
 * <p>V1 范围说明：be 只聚合已落地的 demand 主表口径（确认/发货单数 + 量），
 * stock_flow / return / 车间效能等跨模块明细在 V1 给 0 占位（CROSS-FLOW-003 D14 联动后回填），
 * 避免本 ticket 跨 5 模块硬聚合引入耦合。mp 端按字段渲染，0 值正常显示。</p>
 *
 * @author djs
 * @since WMS-DEMAND-002
 */
@Data
public class DispatchStatsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计日期（回显，yyyy-MM-dd）。 */
    private String statDate;

    // ===== dashboard 1：车间效能（V1 用当日已确认单数代理）=====

    /** 当日已确认需求单数。 */
    private Integer confirmedDemands;

    /** 当日完成需求单数（status=COMPLETED）。 */
    private Integer completedDemands;

    // ===== dashboard 2：进出库（V1 用 demand 累计发货量代理，stock_flow 明细 D14 回填）=====

    /** 当日累计已发货量（demand.shipped_count 当日 SUM）。 */
    private BigDecimal shippedQuantity;

    /** 当日累计已确认量（demand.confirmed_count 当日 SUM）。 */
    private BigDecimal confirmedQuantity;

    // ===== dashboard 3：损耗（V1 占位 0，D14 stock_flow LOSS 回填）=====

    /** 当日损耗量（V1=0 占位）。 */
    private BigDecimal lossQuantity;

    // ===== dashboard 4：退货（V1 占位 0，return_product 模块联动后回填）=====

    /** 当日退货单数（V1=0 占位）。 */
    private Integer returnCount;

    // ===== dashboard 5：发货（V1 用当日待发货单数）=====

    /** 当日待发货需求单数（status IN CONFIRMED/IN_PRODUCTION/PARTIAL_SHIPPED）。 */
    private Integer toShipDemands;
}
