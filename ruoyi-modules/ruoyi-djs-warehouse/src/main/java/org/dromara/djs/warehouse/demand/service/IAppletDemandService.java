package org.dromara.djs.warehouse.demand.service;

import org.dromara.djs.warehouse.demand.domain.vo.DispatchHomeVo;
import org.dromara.djs.warehouse.demand.domain.vo.DispatchListItemVo;
import org.dromara.djs.warehouse.demand.domain.vo.DispatchStatsVo;

import java.time.LocalDate;
import java.util.List;

/**
 * mp 调度员视角需求服务（WMS-DEMAND-002）。
 *
 * <p>仅承载 mp 调度首页 / 列表 / 统计的<b>只读聚合 + enrich</b>。状态推进（确认）/ 指定猪只
 * 一律复用 admin 端 {@link IDemandStatusService#transition} / {@link IDemandManageService#assignPigs}，
 * <b>本 service 不复写状态机逻辑</b>（Kevin 偏好：复用 service 不复写状态机）。</p>
 *
 * @author djs
 * @since WMS-DEMAND-002
 */
public interface IAppletDemandService {

    /**
     * 调度首页 KPI + 入口卡数字（当日 {@code demand_date = CURDATE()} 口径）。
     *
     * @return 白条/蔬菜入口卡 + 今日 KPI 聚合
     */
    DispatchHomeVo dispatchHome();

    /**
     * 白条/蔬菜需求列表（mp 调度员视角，按 storeId 分组前的扁平列表，service enrich storeName）。
     *
     * <p>固定过滤 {@code product_type = productType} AND {@code demand_status IN
     * (SUBMITTED, CONFIRMED, IN_PRODUCTION, PARTIAL_SHIPPED)}（草稿不调度、已完成/取消不展示），
     * 按 demand_date DESC, store_id 排序，最多 200 条。白条额外 enrich {@code assignedPigCount}。</p>
     *
     * @param productType {@code white_bar} / {@code vegetable}
     * @return 列表项（mp 端前端按 storeId 分组成卡）
     */
    List<DispatchListItemVo> dispatchList(String productType);

    /**
     * 统计页 5 dashboard 聚合数字（当日口径）。
     *
     * @param date 统计日期；null 时取今天
     * @return 5 dashboard 聚合 VO
     */
    DispatchStatsVo dispatchStats(LocalDate date);
}
