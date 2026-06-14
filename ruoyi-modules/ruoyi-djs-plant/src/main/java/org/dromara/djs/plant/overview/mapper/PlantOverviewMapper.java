package org.dromara.djs.plant.overview.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.plant.overview.domain.vo.CropDetailVo;
import org.dromara.djs.plant.overview.domain.vo.CropOverviewCardVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 种植总览聚合查询 Mapper（FIX-PLT-AD-OVERVIEW-001）。
 *
 * <p>本 mapper 不绑定单表实体；只提供 overview service 调用的原始聚合 SQL。数据源：
 * {@code t_plant_plot_info}（空/已种植地块数）+ {@code t_plant_plant_details}（产量聚合 + 作物卡片
 * + 逐地块明细）+ {@code t_plant_crop_info}（作物名/缩略图）+ {@code t_plant_plant_plan}（种植季/计划
 * 模糊文案）+ {@code t_plant_work_team}（种植人员）。</p>
 *
 * <p>V1 未启全局 MP 多租户拦截器，{@code @Select} 原生 SQL 不会被自动注入 tenant 过滤，故所有 SQL
 * 显式 {@code WHERE tenant_id = #{tenantId} AND del_flag = '0'}（参照 {@code PlantDashboardMapper}
 * 范式）。{@code plot_status} 为 TINYINT，用整数字面量比较（1=空闲 / 2=种植 / 3=采摘）。</p>
 *
 * <p>产量字段（expected_yield / actual_yield）库内单位为 <b>kg</b>；KPI 吨换算在 service/VO 做。</p>
 *
 * @author djs
 * @since FIX-PLT-AD-OVERVIEW-001
 */
@Mapper
public interface PlantOverviewMapper {

    // ============================ summary：地块数 KPI ============================

    /**
     * 地块状态计数 + 产量汇总（一行聚合）。
     *
     * <p>地块状态计数仅从 {@code t_plant_plot_info} 取（idle/planted）；产量汇总从
     * {@code t_plant_plant_details} 取（kg）。两表口径不同（地块 vs 明细行），分两条 query 避免
     * JOIN 放大行数导致 SUM 重复累加，故本条只算地块计数。</p>
     *
     * @param tenantId 租户
     * @return 单行 {idlePlotCount, plantedPlotCount}（可能为 0）
     */
    @Select("SELECT "
        + "  COALESCE(SUM(CASE WHEN plot_status = 1 THEN 1 ELSE 0 END), 0) AS idlePlotCount, "
        + "  COALESCE(SUM(CASE WHEN plot_status = 2 THEN 1 ELSE 0 END), 0) AS plantedPlotCount "
        + "FROM t_plant_plot_info "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0'")
    PlotCountRow selectPlotCount(@Param("tenantId") String tenantId);

    /**
     * 全局产量汇总（kg），从明细表聚合。
     *
     * @param tenantId 租户
     * @return 单行 {harvestedKg, expectedKg}（已采摘 actual_yield / 预计 expected_yield 合计 kg）
     */
    @Select("SELECT "
        + "  COALESCE(SUM(actual_yield), 0)   AS harvestedKg, "
        + "  COALESCE(SUM(expected_yield), 0) AS expectedKg "
        + "FROM t_plant_plant_details "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0'")
    YieldSumRow selectYieldSum(@Param("tenantId") String tenantId);

    // ============================ summary：作物卡片 ============================

    /**
     * 按作物聚合卡片指标（每作物一行）。
     *
     * <p>从 {@code t_plant_plant_details} 按 crop_id GROUP BY，JOIN 作物名/缩略图。指标：</p>
     * <ul>
     *   <li>计划组：planPlotCount=COUNT(DISTINCT plot_id) / planArea=SUM(plot_area) /
     *       planExpectedYield=SUM(expected_yield kg)</li>
     *   <li>已完成组：donePlotCount=已开始种植地块数(begin_actualdate IS NOT NULL) /
     *       doneArea=已开始种植地块面积 / doneHarvestYield=SUM(actual_yield kg)</li>
     * </ul>
     * <p>currentPlantedPlotCount / currentPlantedArea 复用已完成口径（卡片头展示）。</p>
     *
     * @param tenantId 租户
     * @return 每作物一行卡片 VO；无数据返空 list（最多 200 作物）
     */
    @Select("SELECT "
        + "  d.crop_id AS cropId, "
        + "  c.crop_name AS cropName, "
        + "  c.image_oss_id AS cropImageOssId, "
        + "  COUNT(DISTINCT d.plot_id) AS planPlotCount, "
        + "  COALESCE(SUM(d.plot_area), 0) AS planArea, "
        + "  COALESCE(SUM(d.expected_yield), 0) AS planExpectedYield, "
        + "  COUNT(DISTINCT CASE WHEN d.begin_actualdate IS NOT NULL THEN d.plot_id END) AS donePlotCount, "
        + "  COALESCE(SUM(CASE WHEN d.begin_actualdate IS NOT NULL THEN d.plot_area ELSE 0 END), 0) AS doneArea, "
        + "  COALESCE(SUM(d.actual_yield), 0) AS doneHarvestYield, "
        + "  COUNT(DISTINCT CASE WHEN d.begin_actualdate IS NOT NULL THEN d.plot_id END) AS currentPlantedPlotCount, "
        + "  COALESCE(SUM(CASE WHEN d.begin_actualdate IS NOT NULL THEN d.plot_area ELSE 0 END), 0) AS currentPlantedArea "
        + "FROM t_plant_plant_details d "
        + "LEFT JOIN t_plant_crop_info c ON c.id = d.crop_id AND c.tenant_id = #{tenantId} AND c.del_flag = '0' "
        + "WHERE d.tenant_id = #{tenantId} AND d.del_flag = '0' "
        + "GROUP BY d.crop_id, c.crop_name, c.image_oss_id "
        + "ORDER BY c.crop_name "
        + "LIMIT 200")
    List<CropOverviewCardVo> selectCropCards(@Param("tenantId") String tenantId);

    // ============================ cropDetail：逐地块明细分页 ============================

    /**
     * 按作物分页查询逐地块明细（14 列）。
     *
     * <p>JOIN 地块名 / 种植班组名 / 计划主表（种植季 plan_season + 计划模糊文案 plant_date）。
     * 种植人员取种植班组 {@code plant_by → t_plant_work_team.team_name}（班组软删后仍解析历史名，
     * 故 team JOIN 不带 del_flag）。</p>
     *
     * @param page     MP 分页对象
     * @param tenantId 租户
     * @param cropId   作物 id
     * @return 当前作物逐地块明细分页
     */
    @Select("SELECT "
        + "  d.id AS id, "
        + "  c.crop_name AS cropName, "
        + "  pl.plot_code AS plotCode, "
        + "  pl.plot_name AS plotName, "
        + "  d.harvest_status AS harvestStatus, "
        + "  pp.plan_season AS planSeason, "
        + "  pp.plant_date AS planPlantDate, "
        + "  d.begin_actualdate AS plantDate, "
        + "  t.team_name AS plantTeamName, "
        + "  d.begin_harvestdate AS beginHarvestdate, "
        + "  d.end_harvestdate AS endHarvestdate, "
        + "  d.earliest_harvestdate AS earliestHarvestdate, "
        + "  d.last_harvestdate AS lastHarvestdate, "
        + "  d.plot_area AS plotArea, "
        + "  d.expected_yield AS expectedYield, "
        + "  d.actual_yield AS actualYield "
        + "FROM t_plant_plant_details d "
        + "LEFT JOIN t_plant_crop_info c ON c.id = d.crop_id AND c.tenant_id = #{tenantId} AND c.del_flag = '0' "
        + "LEFT JOIN t_plant_plot_info pl ON pl.id = d.plot_id AND pl.tenant_id = #{tenantId} AND pl.del_flag = '0' "
        + "LEFT JOIN t_plant_plant_plan pp ON pp.id = d.plant_id AND pp.tenant_id = #{tenantId} AND pp.del_flag = '0' "
        + "LEFT JOIN t_plant_work_team t ON t.id = d.plant_by AND t.tenant_id = #{tenantId} "
        + "WHERE d.tenant_id = #{tenantId} AND d.del_flag = '0' AND d.crop_id = #{cropId} "
        + "ORDER BY pl.plot_name, d.id")
    IPage<CropDetailVo> selectCropDetailPage(IPage<CropDetailVo> page,
                                             @Param("tenantId") String tenantId,
                                             @Param("cropId") Long cropId);

    /**
     * 按作物查询逐地块明细全量（导出用，不分页）。
     *
     * @param tenantId 租户
     * @param cropId   作物 id
     * @return 当前作物全部逐地块明细（最多 5000 行）
     */
    @Select("SELECT "
        + "  d.id AS id, "
        + "  c.crop_name AS cropName, "
        + "  pl.plot_code AS plotCode, "
        + "  pl.plot_name AS plotName, "
        + "  d.harvest_status AS harvestStatus, "
        + "  pp.plan_season AS planSeason, "
        + "  pp.plant_date AS planPlantDate, "
        + "  d.begin_actualdate AS plantDate, "
        + "  t.team_name AS plantTeamName, "
        + "  d.begin_harvestdate AS beginHarvestdate, "
        + "  d.end_harvestdate AS endHarvestdate, "
        + "  d.earliest_harvestdate AS earliestHarvestdate, "
        + "  d.last_harvestdate AS lastHarvestdate, "
        + "  d.plot_area AS plotArea, "
        + "  d.expected_yield AS expectedYield, "
        + "  d.actual_yield AS actualYield "
        + "FROM t_plant_plant_details d "
        + "LEFT JOIN t_plant_crop_info c ON c.id = d.crop_id AND c.tenant_id = #{tenantId} AND c.del_flag = '0' "
        + "LEFT JOIN t_plant_plot_info pl ON pl.id = d.plot_id AND pl.tenant_id = #{tenantId} AND pl.del_flag = '0' "
        + "LEFT JOIN t_plant_plant_plan pp ON pp.id = d.plant_id AND pp.tenant_id = #{tenantId} AND pp.del_flag = '0' "
        + "LEFT JOIN t_plant_work_team t ON t.id = d.plant_by AND t.tenant_id = #{tenantId} "
        + "WHERE d.tenant_id = #{tenantId} AND d.del_flag = '0' AND d.crop_id = #{cropId} "
        + "ORDER BY pl.plot_name, d.id "
        + "LIMIT 5000")
    List<CropDetailVo> selectCropDetailList(@Param("tenantId") String tenantId,
                                            @Param("cropId") Long cropId);

    /**
     * 作物名（导出文件名 / 详情标题用）。
     *
     * @param tenantId 租户
     * @param cropId   作物 id
     * @return 作物名，无则 null
     */
    @Select("SELECT crop_name FROM t_plant_crop_info "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND id = #{cropId} LIMIT 1")
    String selectCropName(@Param("tenantId") String tenantId, @Param("cropId") Long cropId);

    /**
     * 地块状态计数载体（mapper 内部用）。
     */
    @lombok.Data
    class PlotCountRow {

        /** 空地块数（plot_status=1）。 */
        private Integer idlePlotCount;

        /** 已种植地块数（plot_status=2）。 */
        private Integer plantedPlotCount;
    }

    /**
     * 产量汇总载体（kg，mapper 内部用）。
     */
    @lombok.Data
    class YieldSumRow {

        /** 已采摘总量（kg，SUM(actual_yield)）。 */
        private BigDecimal harvestedKg;

        /** 预计总产量（kg，SUM(expected_yield)）。 */
        private BigDecimal expectedKg;
    }
}
