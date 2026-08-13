package org.dromara.djs.store.demand.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 门店需求「按天聚合」卡片 VO（mp 需求下单首页，V6 row66）。
 *
 * <p>一行 = 门店某一天的全部需求汇总（需求主表一行 = 一门店 + 一产品 + 一需求日期，无订单头表，
 * 所以「一天一张卡」是查询期聚合出来的，不是落库结构）。</p>
 *
 * <p><b>统计口径统一排除门店态 {@code DELETED} 的行</b>（已取消 / 已删除不参与任何分子分母）；
 * 某天全部行都是 DELETED → 该天整张卡不出现在列表里。</p>
 *
 * @author djs
 * @since STORE-MP-BOARD-001
 */
@Data
public class StoreDemandDayVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 需求日期 {@code yyyy-MM-dd}。 */
    private String demandDate;

    /** 门店 ID（snowflake，Jackson 序列化为 string）。 */
    private Long storeId;

    /** 下单人 = 当天<b>最后一条</b>需求的 {@code create_by} 对应 {@code sys_user.nick_name}。 */
    private String ordererName;

    /** 需求品类数 = 当天 {@code COUNT(DISTINCT product_id)}。 */
    private Integer categoryCount;

    /**
     * 需求确认率 0~1（4 位小数）= 已确认需求单数 / 需求单总数。
     *
     * <p>与 admin {@code DemandGroupVo.confirmRate} 同口径（按<b>需求单</b>而非门店）；
     * 「已确认」= 门店态 ∈ {CONFIRMED, SHIPPED, ARRIVED}。</p>
     */
    private BigDecimal confirmRate;

    /**
     * 当天整体状态（单调阶梯，取当天所有非 DELETED 行的门店态集合 S）：
     * {@code ARRIVED 已到店（S ⊆ {ARRIVED}）/ SHIPPED 已发货（S ⊆ {SHIPPED,ARRIVED}）/
     * IN_PRODUCTION 需求生产中（S ⊆ {CONFIRMED,SHIPPED,ARRIVED}）/ CONFIRMING 需求确认中（其余）}。
     */
    private String dayStatus;

    /** 计损量 = 当天全部需求行关联的损坏件数之和（{@code product_production.is_damaged=1}）。 */
    private Integer damagedCount;

    /** 当天最后下单时间 {@code yyyy-MM-dd HH:mm}（{@code MAX(create_time)} 精确到分）。 */
    private String lastOrderTime;
}
