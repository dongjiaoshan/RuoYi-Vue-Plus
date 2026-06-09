package org.dromara.djs.plant.dashboard.applet.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.CropAreaShareVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.MonthYieldRankVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PickRecordVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PlantAnnualPlanVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PlantManageOverviewVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PlantPlanTimelineVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PlantRecordVo;

import java.util.List;

/**
 * mp 管理·种植管理看板（+ 种植/采摘计划子页）只读聚合 service（FIX-MGMT-MP-PLT-001）。
 *
 * <p>独立 mp 看板链，不复用 admin A 版（口径不符 + perm 墙）、不碰 worker 包。8 个只读端点。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-PLT-001
 */
public interface IAppletPlantManageDashboardService {

    /**
     * 农场种植情况一览 6 KPI。
     *
     * @return overview VO（无数据时各计数 0 / 面积 ZERO）
     */
    PlantManageOverviewVo getOverview();

    /**
     * 年度果蔬规划与执行 6 KPI。
     *
     * @param year 计划年份（空时取当前年）
     * @return annualPlan VO（无数据时各值 0）
     */
    PlantAnnualPlanVo getAnnualPlan(Integer year);

    /**
     * 当前农场果蔬分布（进行中计划按作物分面积，喂饼图）。
     *
     * @return 作物面积片列表，无则空列表
     */
    List<CropAreaShareVo> getCropAreaShare();

    /**
     * 后三个月果蔬预计产量排行（作物 × 月）。
     *
     * @return 排行格列表，无则空列表
     */
    List<MonthYieldRankVo> getYieldRank3m();

    /**
     * 种植计划时间轴（按 plant_month 分组，月头汇总 + 作物卡）。
     *
     * @param year 计划年份（空时取当前年）
     * @return 月度时间轴列表，无则空列表
     */
    List<PlantPlanTimelineVo> getPlanTimeline(Integer year);

    /**
     * 种植记录列表（分页）。
     *
     * @param month     月份过滤（可空）
     * @param cropId    作物过滤（可空）
     * @param pageQuery 分页参数
     * @return 种植记录分页
     */
    TableDataInfo<PlantRecordVo> getPlanRecords(Integer month, Long cropId, PageQuery pageQuery);

    /**
     * 采摘计划时间轴（按计划采摘月分组，同结构复用 timeline VO）。
     *
     * @param year 年份（按计划采摘日年份，空时取当前年）
     * @return 月度时间轴列表，无则空列表
     */
    List<PlantPlanTimelineVo> getPickTimeline(Integer year);

    /**
     * 采摘记录列表（分页）。
     *
     * @param month     月份过滤（可空）
     * @param cropId    作物过滤（可空）
     * @param pickType  采摘类型过滤 is_pick（1=活动 / 2=基地，可空）
     * @param pageQuery 分页参数
     * @return 采摘记录分页
     */
    TableDataInfo<PickRecordVo> getPickRecords(Integer month, Long cropId, Integer pickType, PageQuery pageQuery);

}
