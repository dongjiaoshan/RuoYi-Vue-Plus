package org.dromara.djs.warehouse.demand.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 需求管理页顶部「明日全局」KPI 横条（DJS-FIX-ADMIN-W22-007）。
 *
 * <p>一次返 6 个跨业态数字，供 4 业态 index.vue 顶部横向对比"今天哪种业态压力大"。
 * 与 {@link DemandSummaryVo}（当前业态摘要）互不替代：KPI 横条在 SummaryBar 上方。</p>
 *
 * <p>口径（{@code demand_date = 明日}，明日 = Asia/Shanghai 今天 +1 天，由 service 传入）：</p>
 * <ul>
 *   <li>白条需求头数 = 主表 {@code product_type='white_bar'} 明日 {@code SUM(demand_quantity)}</li>
 *   <li>白条已调配头数 = 子表 {@code t_warehouse_demand_pig} 明日白条下 {@code COUNT(DISTINCT ear_no)}</li>
 *   <li>果蔬需求/已调配品种数 = {@code product_type='vegetable'} 明日 {@code COUNT(DISTINCT product_id)}，
 *       已调配限「已确认」状态集合</li>
 *   <li>其他需求/已调配条数 = {@code product_type IN ('other','gift_box')} 明日条数，已调配限「已确认」状态集合</li>
 * </ul>
 *
 * <p>「已调配」状态集合：{@code CONFIRMED / IN_PRODUCTION / PARTIAL_SHIPPED / COMPLETED}
 * （已脱离 DRAFT/SUBMITTED 待确认态、未 CANCELLED）。</p>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-007
 */
@Data
public class DemandTodayKpiVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 明日白条猪需求头数（头）：仅统计【白条整只】+【白条半只】两类，
     * 整只按 1 头、半只按 0.5 头折算（产品名含「整只」=整只 / 含「半只」=半只；
     * 猪头 / 猪蹄不计入头数）。可含 0.5 小数，故用 {@link BigDecimal}。
     */
    private BigDecimal todayPigDemand;

    /** 明日白条猪已调配头数（demand_pig 子表明日白条下去重耳号数）。 */
    private Integer todayPigAssigned;

    /** 明日果蔬需求品种数（vegetable 明日 COUNT DISTINCT product_id）。 */
    private Integer todayVegSpeciesDemand;

    /** 明日果蔬已调配品种数（同上，限已确认状态集合）。 */
    private Integer todayVegSpeciesAssigned;

    /** 明日其他需求条数（other + gift_box 明日条数）。 */
    private Integer todayOtherDemand;

    /** 明日其他已调配条数（同上，限已确认状态集合）。 */
    private Integer todayOtherAssigned;
}
