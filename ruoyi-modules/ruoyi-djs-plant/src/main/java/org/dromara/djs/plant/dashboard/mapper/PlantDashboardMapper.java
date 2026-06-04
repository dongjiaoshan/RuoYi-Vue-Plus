package org.dromara.djs.plant.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.plant.dashboard.domain.vo.FarmWorkCountVo;
import org.dromara.djs.plant.dashboard.domain.vo.GanttItemVo;
import org.dromara.djs.plant.dashboard.domain.vo.MonthCompletionItemVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 种植看板聚合查询 Mapper（PLT-DASH-001）。
 *
 * <p>本 mapper 不绑定单表实体；只提供 dashboard service 调用的原始聚合 SQL。
 * 数据源：{@code t_plant_plot_info}（土地总览）+ {@code t_plant_farm_records}（今日农事）
 * + {@code t_plant_plant_details}（当月完成率 + 双甘特）+ {@code t_plant_crop_info}（作物名）。</p>
 *
 * <p>dashboard 聚合不走 BaseMapperPlus（不绑实体），故所有 SQL 显式
 * {@code WHERE tenant_id = #{tenantId} AND del_flag = '0'}（参照
 * {@code WarehouseDashboardMapper} 范式，不依赖 MP 租户拦截器自动注入）。</p>
 *
 * <p>关键列类型约定：{@code t_plant_plot_info.plot_status} 是 TINYINT（值 1=空闲 / 2=种植 /
 * 3=采摘），故 SQL 用整数字面量比较（{@code plot_status = 1}），不用字符串引号避免隐式转换。</p>
 *
 * @author djs
 * @since PLT-DASH-001
 */
@Mapper
public interface PlantDashboardMapper {

    // ============================ 块 ① 土地总览 + 今日农事 ============================

    /**
     * 各状态地块数 + 总数 + 总面积（一条聚合 query）。
     *
     * <p>返回单行 Map：idleCount / plantingCount / harvestingCount / totalCount（Integer）
     * + totalArea（BigDecimal）。{@code plot_status} 为 TINYINT，用整数字面量比较。</p>
     *
     * @param tenantId 租户
     * @return 单行聚合（各字段可能为 0，总面积可能为 0）
     */
    @Select("SELECT COUNT(*)                                            AS totalCount, "
        + "       COALESCE(SUM(CASE WHEN plot_status = 1 THEN 1 ELSE 0 END), 0) AS idleCount, "
        + "       COALESCE(SUM(CASE WHEN plot_status = 2 THEN 1 ELSE 0 END), 0) AS plantingCount, "
        + "       COALESCE(SUM(CASE WHEN plot_status = 3 THEN 1 ELSE 0 END), 0) AS harvestingCount, "
        + "       COALESCE(SUM(plot_area), 0)                           AS totalArea "
        + "  FROM t_plant_plot_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0'")
    PlotOverviewRow selectPlotOverview(@Param("tenantId") String tenantId);

    /**
     * 待种地块数（存在 {@code plant_status='pending'} 且 {@code begin_actualdate IS NULL}
     * 明细的不重复地块数）。
     *
     * @param tenantId 租户
     * @return 不重复待种地块数，无则 0
     */
    @Select("SELECT COUNT(DISTINCT d.plot_id) "
        + "  FROM t_plant_plant_details d "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND d.plant_status = 'pending' "
        + "   AND d.begin_actualdate IS NULL")
    Integer countPendingPlot(@Param("tenantId") String tenantId);

    /**
     * 今日农事按类型分桶（{@code farm_date = CURDATE()} GROUP BY {@code farm_type}）。
     *
     * @param tenantId 租户
     * @return 每个农事类型一行 {farmType, count}，无则空列表
     */
    @Select("SELECT farm_type AS farmType, COUNT(*) AS count "
        + "  FROM t_plant_farm_records "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND farm_date = CURDATE() "
        + " GROUP BY farm_type "
        + " ORDER BY count DESC")
    List<FarmWorkCountVo> selectTodayFarmWork(@Param("tenantId") String tenantId);

    /**
     * 今日农事总条数（{@code farm_date = CURDATE()}）。
     *
     * @param tenantId 租户
     * @return 今日农事记录数，无则 0
     */
    @Select("SELECT COUNT(*) "
        + "  FROM t_plant_farm_records "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND farm_date = CURDATE()")
    Integer countTodayFarmWorkTotal(@Param("tenantId") String tenantId);

    // ============================ 块 ② 当月完成率 ============================

    /**
     * 当月种植任务完成率（当月开始种植的明细，按作物分组）。
     *
     * <p>完成率口径：{@code SUM(actual_yield) / SUM(expected_yield)}，前端算柱高（分母 0 → 0）。
     * be 只返原始两值。当月过滤用 {@code begin_actualdate >= 当月 1 号}——未录入 begin_actualdate
     * （NULL）的明细被排除，符合"当月开始种植"语义（非 bug）。</p>
     *
     * @param tenantId 租户
     * @return 每个作物一行 {cropName, actualYield, expectedYield}，无则空列表
     */
    @Select("SELECT c.crop_name                        AS cropName, "
        + "       COALESCE(SUM(d.actual_yield), 0)   AS actualYield, "
        + "       COALESCE(SUM(d.expected_yield), 0) AS expectedYield "
        + "  FROM t_plant_plant_details d "
        + "  LEFT JOIN t_plant_crop_info c ON c.id = d.crop_id AND c.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND d.begin_actualdate >= DATE_FORMAT(NOW(), '%Y-%m-01') "
        + " GROUP BY d.crop_id, c.crop_name "
        + " ORDER BY c.crop_name "
        + " LIMIT 30")
    List<MonthCompletionItemVo> selectMonthCompletion(@Param("tenantId") String tenantId);

    // ============================ 块 ③ 双甘特 ============================

    /**
     * 双甘特原始行（4 时间字段 JOIN crop/plot 出 text）。
     *
     * <p>只取至少有一段日期（种植 begin 或采摘 begin 非 null）的明细。service 层把每行拆成
     * 种植段 / 采摘段两个 {@link GanttItemVo}，日期 null 的段跳过。</p>
     *
     * @param tenantId 租户
     * @return 甘特原始行列表（最多 100 行），无则空列表
     */
    @Select("SELECT d.id                 AS id, "
        + "       c.crop_name           AS cropName, "
        + "       p.plot_name           AS plotName, "
        + "       d.begin_actualdate    AS beginActualdate, "
        + "       d.end_actualdate      AS endActualdate, "
        + "       d.begin_harvestdate   AS beginHarvestdate, "
        + "       d.end_harvestdate     AS endHarvestdate, "
        + "       d.plant_status        AS plantStatus, "
        + "       d.harvest_status      AS harvestStatus "
        + "  FROM t_plant_plant_details d "
        + "  LEFT JOIN t_plant_crop_info c ON c.id = d.crop_id AND c.del_flag = '0' "
        + "  LEFT JOIN t_plant_plot_info p ON p.id = d.plot_id AND p.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND (d.begin_actualdate IS NOT NULL OR d.begin_harvestdate IS NOT NULL) "
        + " ORDER BY d.begin_actualdate "
        + " LIMIT 100")
    List<GanttRow> selectGanttRows(@Param("tenantId") String tenantId);

    /**
     * 土地总览单行聚合载体（mapper 内部用）。
     */
    @lombok.Data
    class PlotOverviewRow {

        /** 地块总数。 */
        private Integer totalCount;

        /** 空闲地块数（plot_status=1）。 */
        private Integer idleCount;

        /** 种植中地块数（plot_status=2）。 */
        private Integer plantingCount;

        /** 采摘中地块数（plot_status=3）。 */
        private Integer harvestingCount;

        /** 土地总面积（亩）。 */
        private BigDecimal totalArea;

    }

    /**
     * 甘特原始行（mapper 内部用，service 层拆成 plant/pick 两段）。
     */
    @lombok.Data
    class GanttRow {

        /** 明细 snowflake id（拼接前缀后作为甘特条 id）。 */
        private Long id;

        /** 作物名称。 */
        private String cropName;

        /** 地块名称。 */
        private String plotName;

        /** 种植段开始日期。 */
        private java.time.LocalDate beginActualdate;

        /** 种植段结束日期。 */
        private java.time.LocalDate endActualdate;

        /** 采摘段开始日期。 */
        private java.time.LocalDate beginHarvestdate;

        /** 采摘段结束日期。 */
        private java.time.LocalDate endHarvestdate;

        /** 种植状态（字典 djs_plant_plan_status）。 */
        private String plantStatus;

        /** 采摘状态（字典 djs_pick_status）。 */
        private String harvestStatus;

    }

}
