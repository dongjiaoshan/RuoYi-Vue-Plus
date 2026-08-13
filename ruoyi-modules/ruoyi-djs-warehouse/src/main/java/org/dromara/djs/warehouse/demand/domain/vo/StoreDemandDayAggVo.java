package org.dromara.djs.warehouse.demand.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门店需求「按天聚合」原始行（mp 门店需求卡 row66 的 SQL 输出，未派生）。
 *
 * <p>只承接 mapper 的聚合结果；门店日状态 {@code dayStatus} 与确认率 {@code confirmRate}
 * 由 store 侧 service 从这里的计数派生（口径集中在 Java 便于单测，不在 SQL 里堆 CASE）。
 * 对外返给 mp 的是 {@code org.dromara.djs.store.demand.domain.vo.StoreDemandDayVo}，不是本类。</p>
 *
 * <p>所有计数<b>已在 SQL 层排除门店态 DELETED 的行</b>（{@code demand_status NOT IN ('DELETED','CANCELLED')}
 * + {@code del_flag='0'}），故 {@code totalCount} 恒 ≥ 1（整天全删的日期不会出现在结果里）。</p>
 *
 * @author djs
 * @since STORE-MP-BOARD-001
 */
@Data
public class StoreDemandDayAggVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 需求日期 {@code yyyy-MM-dd}（SQL DATE_FORMAT 定格，不依赖 Jackson 日期格式）。 */
    private String demandDate;

    /** 门店 ID。 */
    private Long storeId;

    /** 下单人昵称 = 当天最后一条需求（create_time / id 倒序第一条）的 {@code sys_user.nick_name}。 */
    private String ordererName;

    /** 需求品类数 = 当天 {@code COUNT(DISTINCT product_id)}。 */
    private Integer categoryCount;

    /** 当天需求单总数（分母）。 */
    private Integer totalCount;

    /** 当天门店态 = ARRIVED 的需求单数。 */
    private Integer arrivedCount;

    /** 当天门店态 = SHIPPED 的需求单数。 */
    private Integer shippedCount;

    /** 当天门店态 = CONFIRMED 的需求单数。 */
    private Integer confirmedCount;

    /** 当天全部需求行的损坏件数之和（{@code product_production.is_damaged=1}）。 */
    private Integer damagedCount;

    /** 当天最后下单时间 {@code yyyy-MM-dd HH:mm}（{@code MAX(create_time)} 精确到分）。 */
    private String lastOrderTime;
}
