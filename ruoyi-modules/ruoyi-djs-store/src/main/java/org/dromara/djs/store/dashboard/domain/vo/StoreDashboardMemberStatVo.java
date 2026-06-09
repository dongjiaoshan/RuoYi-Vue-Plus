package org.dromara.djs.store.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门店看板 - 会员当日 4 格统计（FIX-MGMT-MP-STORE-001）。
 *
 * <p>来源：{@code t_store_member} 按 {@code create_time} 聚合，门店可选过滤。原型门店看板
 * "当日会员数量统计 4 格"：会员总数 / 今日新增 / 今日发展 / 当月新增。</p>
 *
 * <p>V1 无"发展会员"（推荐人 / 渠道）字段，故 {@code todayDeveloped} 与 {@code todayNew}
 * 同义（口径见 assumptions，待客户区分时再拆字段）。所有 count 无数据返 0。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-STORE-001
 */
@Data
public class StoreDashboardMemberStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会员总数（软删不计）。
     */
    private Long totalCount;

    /**
     * 今日新增会员数（{@code DATE(create_time)=今天}）。
     */
    private Long todayNew;

    /**
     * 今日发展会员数（V1 无渠道字段，口径等同今日新增）。
     */
    private Long todayDeveloped;

    /**
     * 当月新增会员数（{@code create_time} 落在当月）。
     */
    private Long monthNew;

    /**
     * 全空兜底实例（无数据时返回，4 格全 0）。
     *
     * @return 4 格全 0 的 VO
     */
    public static StoreDashboardMemberStatVo empty() {
        StoreDashboardMemberStatVo vo = new StoreDashboardMemberStatVo();
        vo.setTotalCount(0L);
        vo.setTodayNew(0L);
        vo.setTodayDeveloped(0L);
        vo.setMonthNew(0L);
        return vo;
    }

}
