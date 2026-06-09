package org.dromara.djs.plant.dashboard.applet.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.plant.dashboard.applet.domain.vo.CropAreaShareVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.MonthYieldRankVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PickRecordVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PlantPlanTimelineVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PlantRecordVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * mp 管理·种植管理看板聚合查询 Mapper（FIX-MGMT-MP-PLT-001）。
 *
 * <p>独立 mp 看板链，不绑单表实体、不复用 worker 包的 PlantDetailsMapper / admin 的
 * {@code PlantDashboardMapper}（A 版口径不符 + 带 perm 墙）。纯只读聚合。</p>
 *
 * <p>数据源：{@code t_plant_plant_details}（计划明细，主源）+ {@code t_plant_crop_info}（作物名/图）
 * + {@code t_plant_plot_info}（地块计数/编码）+ {@code t_plant_plant_plan}（计划年份维度）
 * + {@code t_plant_work_team}（班组名 enrich，作为"员工"口径）。</p>
 *
 * <p>所有 SQL 显式 {@code WHERE tenant_id = #{tenantId} AND del_flag = '0'} + {@code COALESCE} 兜底
 * （参照 {@code WarehouseDashboardMapper} / {@code PlantDashboardMapper} 范式，不依赖 MP 租户拦截器自动注入）。</p>
 *
 * <p>关键列类型约定：{@code t_plant_plot_info.plot_status} 是 TINYINT（1=空闲 / 2=种植中 / 3=采摘），
 * 用整数字面量比较；{@code t_plant_plant_details.is_pick} 是 TINYINT（1=游客采摘活动 / 2=否=基地）。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-PLT-001
 */
@Mapper
public interface AppletPlantManageDashboardMapper {

    // ============================ 端点 1 overview 农场种植情况一览 ============================

    /**
     * 地块计数（总 / 种植中 / 空闲，一条聚合）。{@code plot_status} TINYINT，整数字面量比较。
     *
     * @param tenantId 租户
     * @return 单行 {totalPlotCount, plantingPlotCount, idlePlotCount}，无行各 0
     */
    @Select("SELECT COUNT(*)                                                       AS totalPlotCount, "
        + "       COALESCE(SUM(CASE WHEN plot_status = 2 THEN 1 ELSE 0 END), 0) AS plantingPlotCount, "
        + "       COALESCE(SUM(CASE WHEN plot_status = 1 THEN 1 ELSE 0 END), 0) AS idlePlotCount "
        + "  FROM t_plant_plot_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0'")
    PlotCountRow selectPlotCount(@Param("tenantId") String tenantId);

    /**
     * 已种植果蔬品种数（{@code plant_status <> 'pending'} 的不重复 crop_id 数）。
     *
     * @param tenantId 租户
     * @return 已种品种数，无则 0
     */
    @Select("SELECT COUNT(DISTINCT crop_id) "
        + "  FROM t_plant_plant_details "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND plant_status <> 'pending'")
    Integer countPlantedCrop(@Param("tenantId") String tenantId);

    /**
     * 当前种植面积（亩）：进行中明细（{@code plant_status='ongoing'}）的 {@code plot_area} 合计。
     *
     * @param tenantId 租户
     * @return 面积合计，无则 0
     */
    @Select("SELECT COALESCE(SUM(plot_area), 0) "
        + "  FROM t_plant_plant_details "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND plant_status = 'ongoing'")
    BigDecimal sumCurrentPlantArea(@Param("tenantId") String tenantId);

    /**
     * 近一月可采摘品种数（计划最早采摘日 {@code earliest_harvestdate} 落 [今, 今+1 月] 的不重复 crop_id 数）。
     *
     * @param tenantId 租户
     * @return 可采摘品种数，无则 0
     */
    @Select("SELECT COUNT(DISTINCT crop_id) "
        + "  FROM t_plant_plant_details "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND earliest_harvestdate >= CURDATE() "
        + "   AND earliest_harvestdate < DATE_ADD(CURDATE(), INTERVAL 1 MONTH)")
    Integer countHarvestableCrop(@Param("tenantId") String tenantId);

    // ============================ 端点 2 annualPlan 年度果蔬规划与执行 ============================

    /**
     * 年度规划与执行 6 KPI（一条聚合，按 {@code plant_id} JOIN plan 取 {@code plan_year}）。
     *
     * <p>产量字段（expected_yield / actual_yield）单位 kg；service 层 /1000 转吨。
     * 已种植 = {@code begin_actualdate IS NOT NULL}。</p>
     *
     * @param tenantId 租户
     * @param year     计划年份
     * @return 单行 6 值，无行各 0
     */
    @Select("SELECT COALESCE(SUM(d.plot_area), 0)                                                          AS planArea, "
        + "       COUNT(DISTINCT d.crop_id)                                                              AS planCropCount, "
        + "       COALESCE(SUM(d.expected_yield), 0)                                                     AS expectedYieldKg, "
        + "       COALESCE(SUM(CASE WHEN d.begin_actualdate IS NOT NULL THEN d.plot_area ELSE 0 END), 0) AS plantedArea, "
        + "       COUNT(DISTINCT CASE WHEN d.begin_actualdate IS NOT NULL THEN d.crop_id END)            AS plantedCropCount, "
        + "       COALESCE(SUM(d.actual_yield), 0)                                                       AS harvestedYieldKg "
        + "  FROM t_plant_plant_details d "
        + "  JOIN t_plant_plant_plan pp ON pp.id = d.plant_id AND pp.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND pp.plan_year = #{year}")
    AnnualPlanRow selectAnnualPlan(@Param("tenantId") String tenantId, @Param("year") Integer year);

    // ============================ 端点 3 cropAreaShare 果蔬分布饼图 ============================

    /**
     * 进行中计划（{@code plant_status='ongoing'}）按作物分组面积合计（喂饼图）。
     *
     * @param tenantId 租户
     * @return 每作物一行 {cropName, area}，按面积降序，无则空列表
     */
    @Select("SELECT c.crop_name                  AS cropName, "
        + "       COALESCE(SUM(d.plot_area), 0) AS area "
        + "  FROM t_plant_plant_details d "
        + "  LEFT JOIN t_plant_crop_info c ON c.id = d.crop_id AND c.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND d.plant_status = 'ongoing' "
        + " GROUP BY d.crop_id, c.crop_name "
        + " ORDER BY area DESC "
        + " LIMIT 50")
    List<CropAreaShareVo> selectCropAreaShare(@Param("tenantId") String tenantId);

    // ============================ 端点 4 yieldRank3m 后三月产量排行 ============================

    /**
     * 后三月预计产量排行（{@code earliest_harvestdate} 落 [今, 今+3 月]，按作物+月分组求 expected_yield）。
     *
     * @param tenantId 租户
     * @return 每 作物×月 一格 {cropName, month, expectedYield}（kg），无则空列表
     */
    @Select("SELECT c.crop_name                       AS cropName, "
        + "       MONTH(d.earliest_harvestdate)     AS month, "
        + "       COALESCE(SUM(d.expected_yield), 0) AS expectedYield "
        + "  FROM t_plant_plant_details d "
        + "  LEFT JOIN t_plant_crop_info c ON c.id = d.crop_id AND c.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND d.earliest_harvestdate >= CURDATE() "
        + "   AND d.earliest_harvestdate < DATE_ADD(CURDATE(), INTERVAL 3 MONTH) "
        + " GROUP BY d.crop_id, c.crop_name, MONTH(d.earliest_harvestdate) "
        + " ORDER BY month ASC, expectedYield DESC")
    List<MonthYieldRankVo> selectYieldRank3m(@Param("tenantId") String tenantId);

    // ============================ 端点 5 planTimeline 种植计划时间轴 ============================

    /**
     * 种植计划月度汇总（年内明细按 {@code plant_month} 分组：品种数 / 地块数 / 面积合计）。
     *
     * @param tenantId 租户
     * @param year     计划年份（JOIN plan.plan_year）
     * @return 每月一行（仅有数据的月），无则空列表
     */
    @Select("SELECT d.plant_month                  AS month, "
        + "       COUNT(DISTINCT d.crop_id)      AS cropCount, "
        + "       COUNT(DISTINCT d.plot_id)      AS plotCount, "
        + "       COALESCE(SUM(d.plot_area), 0)  AS totalArea "
        + "  FROM t_plant_plant_details d "
        + "  JOIN t_plant_plant_plan pp ON pp.id = d.plant_id AND pp.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND pp.plan_year = #{year} "
        + " GROUP BY d.plant_month "
        + " ORDER BY d.plant_month ASC")
    List<MonthGroupRow> selectPlanMonthGroups(@Param("tenantId") String tenantId, @Param("year") Integer year);

    /**
     * 种植计划月度内各作物明细（喂 timeline 每月 crops 数组）。
     *
     * @param tenantId 租户
     * @param year     计划年份
     * @return 每 月×作物 一行 {month, cropName, cropImg, plotCount, area}，无则空列表
     */
    @Select("SELECT d.plant_month                  AS month, "
        + "       c.crop_name                    AS cropName, "
        + "       c.crop_image_preview           AS cropImg, "
        + "       COUNT(DISTINCT d.plot_id)      AS plotCount, "
        + "       COALESCE(SUM(d.plot_area), 0)  AS area "
        + "  FROM t_plant_plant_details d "
        + "  JOIN t_plant_plant_plan pp ON pp.id = d.plant_id AND pp.del_flag = '0' "
        + "  LEFT JOIN t_plant_crop_info c ON c.id = d.crop_id AND c.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND pp.plan_year = #{year} "
        + " GROUP BY d.plant_month, d.crop_id, c.crop_name, c.crop_image_preview "
        + " ORDER BY d.plant_month ASC, area DESC")
    List<MonthCropRow> selectPlanMonthCrops(@Param("tenantId") String tenantId, @Param("year") Integer year);

    // ============================ 端点 6 planRecords 种植记录列表（分页） ============================

    /**
     * 种植记录分页（实际已开始种植的明细，{@code begin_actualdate IS NOT NULL}）。
     *
     * <p>"员工"口径 = 种植班组名（JOIN {@code t_plant_work_team} on {@code plant_by}）。月份/作物可选过滤。</p>
     *
     * @param page     MP 分页对象（{@code pageQuery.build()}）
     * @param tenantId 租户
     * @param month    月份过滤（plant_month，可空）
     * @param cropId   作物过滤（可空）
     * @return 种植记录分页
     */
    @Select("<script>"
        + "SELECT d.begin_actualdate AS date, "
        + "       c.crop_name        AS cropName, "
        + "       p.plot_code        AS plotCode, "
        + "       d.plot_area        AS area, "
        + "       t.team_name        AS employee "
        + "  FROM t_plant_plant_details d "
        + "  LEFT JOIN t_plant_crop_info c  ON c.id = d.crop_id  AND c.del_flag = '0' "
        + "  LEFT JOIN t_plant_plot_info p  ON p.id = d.plot_id  AND p.del_flag = '0' "
        + "  LEFT JOIN t_plant_work_team t  ON t.id = d.plant_by AND t.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND d.begin_actualdate IS NOT NULL "
        + "   <if test='month != null'> AND d.plant_month = #{month} </if>"
        + "   <if test='cropId != null'> AND d.crop_id = #{cropId} </if>"
        + " ORDER BY d.begin_actualdate DESC, d.id DESC "
        + "</script>")
    IPage<PlantRecordVo> selectPlanRecordsPage(IPage<PlantRecordVo> page,
                                               @Param("tenantId") String tenantId,
                                               @Param("month") Integer month,
                                               @Param("cropId") Long cropId);

    // ============================ 端点 7 pickTimeline 采摘计划时间轴 ============================

    /**
     * 采摘计划月度汇总（年内明细按计划采摘月 {@code MONTH(earliest_harvestdate)} 分组）。
     *
     * @param tenantId 租户
     * @param year     年份（按计划采摘日年份 {@code YEAR(earliest_harvestdate)}）
     * @return 每月一行，无则空列表
     */
    @Select("SELECT MONTH(d.earliest_harvestdate) AS month, "
        + "       COUNT(DISTINCT d.crop_id)      AS cropCount, "
        + "       COUNT(DISTINCT d.plot_id)      AS plotCount, "
        + "       COALESCE(SUM(d.plot_area), 0)  AS totalArea "
        + "  FROM t_plant_plant_details d "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND YEAR(d.earliest_harvestdate) = #{year} "
        + " GROUP BY MONTH(d.earliest_harvestdate) "
        + " ORDER BY month ASC")
    List<MonthGroupRow> selectPickMonthGroups(@Param("tenantId") String tenantId, @Param("year") Integer year);

    /**
     * 采摘计划月度内各作物明细（喂 timeline 每月 crops 数组，按计划采摘月分组）。
     *
     * @param tenantId 租户
     * @param year     年份（{@code YEAR(earliest_harvestdate)}）
     * @return 每 月×作物 一行，无则空列表
     */
    @Select("SELECT MONTH(d.earliest_harvestdate) AS month, "
        + "       c.crop_name                    AS cropName, "
        + "       c.crop_image_preview           AS cropImg, "
        + "       COUNT(DISTINCT d.plot_id)      AS plotCount, "
        + "       COALESCE(SUM(d.plot_area), 0)  AS area "
        + "  FROM t_plant_plant_details d "
        + "  LEFT JOIN t_plant_crop_info c ON c.id = d.crop_id AND c.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND YEAR(d.earliest_harvestdate) = #{year} "
        + " GROUP BY MONTH(d.earliest_harvestdate), d.crop_id, c.crop_name, c.crop_image_preview "
        + " ORDER BY month ASC, area DESC")
    List<MonthCropRow> selectPickMonthCrops(@Param("tenantId") String tenantId, @Param("year") Integer year);

    // ============================ 端点 8 pickRecords 采摘记录列表（分页） ============================

    /**
     * 采摘记录分页（已实际开始采摘的明细，{@code begin_harvestdate IS NOT NULL}）。
     *
     * <p>"员工"口径 = 采摘班组名（JOIN {@code t_plant_work_team} on {@code harvest_by}）。
     * pickType 来自 {@code is_pick}（1=活动 / 2=基地），label 在 service 翻中文。重量取 {@code actual_yield}（kg）。
     * 月份 / 作物 / 采摘类型可选过滤。</p>
     *
     * @param page     MP 分页对象
     * @param tenantId 租户
     * @param month    月份过滤（MONTH(begin_harvestdate)，可空）
     * @param cropId   作物过滤（可空）
     * @param pickType 采摘类型过滤 is_pick（1/2，可空）
     * @return 采摘记录分页
     */
    @Select("<script>"
        + "SELECT d.begin_harvestdate AS date, "
        + "       d.is_pick           AS isPick, "
        + "       c.crop_name         AS cropName, "
        + "       p.plot_code         AS plotCode, "
        + "       d.actual_yield      AS weightKg, "
        + "       t.team_name         AS employee "
        + "  FROM t_plant_plant_details d "
        + "  LEFT JOIN t_plant_crop_info c  ON c.id = d.crop_id    AND c.del_flag = '0' "
        + "  LEFT JOIN t_plant_plot_info p  ON p.id = d.plot_id    AND p.del_flag = '0' "
        + "  LEFT JOIN t_plant_work_team t  ON t.id = d.harvest_by AND t.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND d.begin_harvestdate IS NOT NULL "
        + "   <if test='month != null'> AND MONTH(d.begin_harvestdate) = #{month} </if>"
        + "   <if test='cropId != null'> AND d.crop_id = #{cropId} </if>"
        + "   <if test='pickType != null'> AND d.is_pick = #{pickType} </if>"
        + " ORDER BY d.begin_harvestdate DESC, d.id DESC "
        + "</script>")
    IPage<PickRecordRow> selectPickRecordsPage(IPage<PickRecordRow> page,
                                               @Param("tenantId") String tenantId,
                                               @Param("month") Integer month,
                                               @Param("cropId") Long cropId,
                                               @Param("pickType") Integer pickType);

    // ============================ mapper 内部行载体 ============================

    /**
     * 地块计数单行（overview 前 3 KPI）。
     */
    @lombok.Data
    class PlotCountRow {

        /** 总地块数。 */
        private Integer totalPlotCount;

        /** 种植中地块数（plot_status=2）。 */
        private Integer plantingPlotCount;

        /** 空闲地块数（plot_status=1）。 */
        private Integer idlePlotCount;

    }

    /**
     * 年度规划与执行单行（产量为 kg，service /1000 转吨）。
     */
    @lombok.Data
    class AnnualPlanRow {

        /** 总计划面积（亩）。 */
        private BigDecimal planArea;

        /** 计划品种数。 */
        private Integer planCropCount;

        /** 预计总产量（kg）。 */
        private BigDecimal expectedYieldKg;

        /** 已种植面积（亩）。 */
        private BigDecimal plantedArea;

        /** 已种植品种数。 */
        private Integer plantedCropCount;

        /** 已采摘总产量（kg）。 */
        private BigDecimal harvestedYieldKg;

    }

    /**
     * 时间轴每月汇总行（service 组装 {@link PlantPlanTimelineVo} 头）。
     */
    @lombok.Data
    class MonthGroupRow {

        /** 月份 1-12。 */
        private Integer month;

        /** 品种数。 */
        private Integer cropCount;

        /** 地块数。 */
        private Integer plotCount;

        /** 面积合计（亩）。 */
        private BigDecimal totalArea;

    }

    /**
     * 时间轴每月各作物行（service 组装 timeline crops 数组）。
     */
    @lombok.Data
    class MonthCropRow {

        /** 月份 1-12。 */
        private Integer month;

        /** 作物名。 */
        private String cropName;

        /** 作物缩略图 OSS objectName（可空，前端解析 url）。 */
        private String cropImg;

        /** 该作物该月地块数。 */
        private Integer plotCount;

        /** 该作物该月面积合计（亩）。 */
        private BigDecimal area;

    }

    /**
     * 采摘记录行（mapper 内部用，service 把 isPick 翻成 pickType 中文）。
     */
    @lombok.Data
    class PickRecordRow {

        /** 实际采摘开始日期。 */
        private java.time.LocalDate date;

        /** is_pick 原值（1=活动 / 2=基地）。 */
        private Integer isPick;

        /** 作物名。 */
        private String cropName;

        /** 地块编码。 */
        private String plotCode;

        /** 采摘重量（kg，actual_yield）。 */
        private BigDecimal weightKg;

        /** 采摘班组名（员工口径）。 */
        private String employee;

    }

}
