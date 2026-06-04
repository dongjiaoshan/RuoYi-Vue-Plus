package org.dromara.djs.store.member.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会员简单统计 VO（STR-MEMBER-001）。
 *
 * <p>V1 只保留两个 count 数字（admin 列表页顶部 2 个 KPI 卡）：本月会员数 + 本月录入消费记录数。
 * 不做 RFM / 客单价 / 复购等营销指标（迁 V2）。</p>
 *
 * @author djs
 * @since STR-MEMBER-001
 */
@Data
public class StoreMemberStatsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 本月新增会员数（按 {@code create_time} 落在本月，软删不计）。
     */
    private Long monthlyMemberCount;

    /**
     * 本月录入消费记录数（按 {@code create_time} 落在本月，软删不计）。
     */
    private Long monthlyConsumptionCount;

}
