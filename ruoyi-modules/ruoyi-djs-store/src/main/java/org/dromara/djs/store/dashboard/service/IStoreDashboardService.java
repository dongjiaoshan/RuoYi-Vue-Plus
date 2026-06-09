package org.dromara.djs.store.dashboard.service;

import org.dromara.djs.store.dashboard.domain.vo.StoreDashboardDailyVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreDashboardMemberStatVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreDashboardSummaryVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreMemberGrowthPointVo;

import java.util.List;

/**
 * 门店看板 Service（STR-DASH-001，纯只读聚合）。
 *
 * <p>admin PC 门店首页（getSummary）+ mp 门店每日情况一览（getDaily）双端复用。
 * 无数据时返回全 0 / 空列表，不抛错。</p>
 *
 * @author djs
 * @since STR-DASH-001
 */
public interface IStoreDashboardService {

    /**
     * 门店看板汇总：6 KPI + 当日产品结构 + 当月 TOP10 + 近 10 日趋势
     * + 4 业态销售分布 + 当日 4 KPI 同比（FIX-MGMT-MP-STORE-001）。
     *
     * <p>同比基准 = 上月同期（同一历史日 sale_date）；上期为 0 时同比返 null（前端显示"—"）。
     * 充值额 V1 无数据源 → 始终 null。</p>
     *
     * @param storeId 门店 ID（可空，null 时按数据权限范围聚合）
     * @return 看板汇总 VO（无数据时计数全 0、列表全空、同比 null）
     */
    StoreDashboardSummaryVo getSummary(Long storeId);

    /**
     * 门店每日情况一览：猪肉 / 果蔬当日速览 + 需求统计 + 发货统计。
     *
     * <p>退回率 / 损耗率上游 V1 无数据源 → 为 null（前端显示"—"）。</p>
     *
     * @param storeId 门店 ID（可空，null 时按数据权限范围聚合）
     * @return 每日一览 VO（无数据时速览全 0、统计列表空）
     */
    StoreDashboardDailyVo getDaily(Long storeId);

    /**
     * 近 10 日客户增长：{@code t_store_member} 按 {@code DATE(create_time)} 每日新增数
     * （FIX-MGMT-MP-STORE-001）。仅出有新增的日期行，前端按 10 日窗口补 0。
     *
     * @param storeId 门店 ID（可空，null 时按数据权限范围聚合）
     * @return 近 10 日增长点列表（按日期升序，无数据返空列表）
     */
    List<StoreMemberGrowthPointVo> getMemberGrowth10Days(Long storeId);

    /**
     * 会员当日 4 格统计：会员总数 / 今日新增 / 今日发展 / 当月新增
     * （FIX-MGMT-MP-STORE-001）。V1 无渠道字段，今日发展口径等同今日新增。
     *
     * @param storeId 门店 ID（可空，null 时按数据权限范围聚合）
     * @return 会员 4 格 VO（无数据时 4 格全 0）
     */
    StoreDashboardMemberStatVo getMemberStat(Long storeId);

}
