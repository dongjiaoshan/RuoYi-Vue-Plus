package org.dromara.djs.plant.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.plant.dashboard.domain.vo.CropPlantStatItemVo;
import org.dromara.djs.plant.dashboard.domain.vo.MonthCompletionItemVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 种植看板聚合查询 Mapper（PLT-DASH-001）。
 *
 * <p>本 mapper 不绑定单表实体；只提供 dashboard service 调用的原始聚合 SQL。
 * 数据源：{@code t_plant_plot_info}（土地总览）+ {@code t_plant_farm_records}（今日空地/种植管理/灾害）
 * + {@code t_plant_plant_details}（当月完成率 + 今日种植/采摘 + 双甘特）
 * + {@code t_plant_pick_activity}（今日采摘活动）+ {@code t_plant_crop_organic}（果蔬有机证书）
 * + {@code t_plant_crop_info}（作物名）。</p>
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
        + "       COALESCE(SUM(CASE WHEN p.plot_status = 1 THEN 1 ELSE 0 END), 0) AS idleCount, "
        + "       COALESCE(SUM(CASE WHEN p.plot_status = 2 THEN 1 ELSE 0 END), 0) AS plantingCount, "
        + "       COALESCE(SUM(CASE WHEN p.plot_status = 3 THEN 1 ELSE 0 END), 0) AS harvestingCount, "
        + "       COALESCE(SUM(p.plot_area), 0)                         AS totalArea "
        + "  FROM t_plant_plot_info p "
        + "  JOIN t_plant_plot_zone z ON z.id = p.zone_id "
        + "                          AND z.tenant_id = #{tenantId} "
        + "                          AND z.del_flag = '0' "
        + "                          AND z.zone_status = 1 "
        + " WHERE p.tenant_id = #{tenantId} "
        + "   AND p.del_flag = '0'")
    PlotOverviewRow selectPlotOverview(@Param("tenantId") String tenantId);

    /**
     * 当前种植面积（亩）：种植中地块（{@code plot_status = 2}）的 {@code SUM(plot_area)}。
     *
     * <p>区块 ① "当前种植面积亩"。仅统计有效片区下的种植中地块。</p>
     *
     * @param tenantId 租户
     * @return 当前种植面积（亩），无则 0
     */
    @Select("SELECT COALESCE(SUM(p.plot_area), 0) "
        + "  FROM t_plant_plot_info p "
        + "  JOIN t_plant_plot_zone z ON z.id = p.zone_id "
        + "                          AND z.tenant_id = #{tenantId} "
        + "                          AND z.del_flag = '0' "
        + "                          AND z.zone_status = 1 "
        + " WHERE p.tenant_id = #{tenantId} "
        + "   AND p.del_flag = '0' "
        + "   AND p.plot_status = 2")
    BigDecimal selectCurrentPlantingArea(@Param("tenantId") String tenantId);

    /**
     * 当前预计产量（kg）：未完成种植明细（{@code plant_status != 'completed'}）的 {@code SUM(expected_yield)}。
     *
     * <p>区块 ① "当前预计产量"。前端按 kg → 万斤换算（{@code kg × 2 / 10000}）。</p>
     *
     * @param tenantId 租户
     * @return 当前预计产量（kg），无则 0
     */
    @Select("SELECT COALESCE(SUM(d.expected_yield), 0) "
        + "  FROM t_plant_plant_details d "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND d.plant_status <> 'completed'")
    BigDecimal selectCurrentExpectedYield(@Param("tenantId") String tenantId);

    /**
     * 待种地块数（存在 {@code plant_status='pending'} 且 {@code begin_actualdate IS NULL}
     * 明细的不重复地块数）。
     *
     * @param tenantId 租户
     * @return 不重复待种地块数，无则 0
     */
    @Select("SELECT COUNT(DISTINCT d.plot_id) "
        + "  FROM t_plant_plant_details d "
        + "  JOIN t_plant_plot_info p ON p.id = d.plot_id "
        + "                          AND p.tenant_id = #{tenantId} "
        + "                          AND p.del_flag = '0' "
        + "  JOIN t_plant_plot_zone z ON z.id = p.zone_id "
        + "                          AND z.tenant_id = #{tenantId} "
        + "                          AND z.del_flag = '0' "
        + "                          AND z.zone_status = 1 "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND d.plant_status = 'pending' "
        + "   AND d.begin_actualdate IS NULL")
    Integer countPendingPlot(@Param("tenantId") String tenantId);

    /**
     * 今日工作 - 种植：今日实际开始种植（{@code begin_actualdate = CURDATE()}）的不重复地块数。
     *
     * @param tenantId 租户
     * @return 不重复地块数，无则 0
     */
    @Select("SELECT COUNT(DISTINCT d.plot_id) "
        + "  FROM t_plant_plant_details d "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND d.begin_actualdate = CURDATE()")
    Integer countTodayPlanting(@Param("tenantId") String tenantId);

    /**
     * 今日工作 - 采摘：今日实际开始采摘（{@code begin_harvestdate = CURDATE()}）的不重复地块数。
     *
     * @param tenantId 租户
     * @return 不重复地块数，无则 0
     */
    @Select("SELECT COUNT(DISTINCT d.plot_id) "
        + "  FROM t_plant_plant_details d "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND d.begin_harvestdate = CURDATE()")
    Integer countTodayHarvest(@Param("tenantId") String tenantId);

    /**
     * 今日工作 - 空地管理：今日农事（{@code farm_date = CURDATE()}）且
     * {@code farm_type IN (tillage_break, tillage_prepare, fertilize)} 的不重复地块数。
     *
     * <p>映射权威 doc/10 §F-PLT-06：空地管理 = 翻耕 / 整地 / 施肥。</p>
     *
     * @param tenantId 租户
     * @return 不重复地块数，无则 0
     */
    @Select("SELECT COUNT(DISTINCT plot_id) "
        + "  FROM t_plant_farm_records "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND farm_date = CURDATE() "
        + "   AND farm_type IN ('tillage_break', 'tillage_prepare', 'fertilize')")
    Integer countTodayIdleMgmt(@Param("tenantId") String tenantId);

    /**
     * 今日工作 - 种植管理：今日农事（{@code farm_date = CURDATE()}）且 {@code farm_type IN
     * (transplant, water_fertilize, irrigation, weed, pruning, pest_control, rotation)} 的不重复地块数。
     *
     * <p>映射权威 doc/10 §F-PLT-06：种植管理 = 移栽 / 水肥 / 浇灌 / 除草 / 整枝绑蔓 / 病虫防治 / 退茬。</p>
     *
     * @param tenantId 租户
     * @return 不重复地块数，无则 0
     */
    @Select("SELECT COUNT(DISTINCT plot_id) "
        + "  FROM t_plant_farm_records "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND farm_date = CURDATE() "
        + "   AND farm_type IN ('transplant', 'water_fertilize', 'irrigation', "
        + "                     'weed', 'pruning', 'pest_control', 'rotation')")
    Integer countTodayPlantMgmt(@Param("tenantId") String tenantId);

    /**
     * 今日工作 - 灾害损失：今日农事（{@code farm_date = CURDATE()}）且 {@code farm_type = 'disaster'}
     * 的不重复地块数。
     *
     * @param tenantId 租户
     * @return 不重复地块数，无则 0
     */
    @Select("SELECT COUNT(DISTINCT plot_id) "
        + "  FROM t_plant_farm_records "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND farm_date = CURDATE() "
        + "   AND farm_type = 'disaster'")
    Integer countTodayDisaster(@Param("tenantId") String tenantId);

    /**
     * 今日工作 - 采摘活动：今日采摘活动（{@code t_plant_pick_activity.activity_date = CURDATE()}）
     * 的采摘量合计 kg（{@code SUM(total_yield)}）。
     *
     * @param tenantId 租户
     * @return 今日采摘量 kg，无则 0
     */
    @Select("SELECT COALESCE(SUM(total_yield), 0) "
        + "  FROM t_plant_pick_activity "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND activity_date = CURDATE()")
    BigDecimal selectTodayPickActivityWeight(@Param("tenantId") String tenantId);

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

    // ============================ 块 ④ 实时种植物统计（by 作物） ============================

    /**
     * 实时种植物统计（按作物分组）：在种地块数 + 预计产量合计。
     *
     * <p>仅统计未完成种植（{@code plant_status != 'completed'}）的明细，按 {@code crop_id} 分组，
     * JOIN {@code t_plant_crop_info} 取作物名。bar = {@code COUNT(DISTINCT plot_id)}，
     * line = {@code SUM(expected_yield)}（kg）。</p>
     *
     * @param tenantId 租户
     * @return 每个作物一行 {cropName, plotCount, expectedYield}，无则空列表（最多 30 行）
     */
    @Select("SELECT c.crop_name                          AS cropName, "
        + "       COUNT(DISTINCT d.plot_id)            AS plotCount, "
        + "       COALESCE(SUM(d.expected_yield), 0)   AS expectedYield "
        + "  FROM t_plant_plant_details d "
        + "  LEFT JOIN t_plant_crop_info c ON c.id = d.crop_id AND c.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND d.plant_status <> 'completed' "
        + " GROUP BY d.crop_id, c.crop_name "
        + " ORDER BY plotCount DESC, c.crop_name "
        + " LIMIT 30")
    List<CropPlantStatItemVo> selectCropPlantStat(@Param("tenantId") String tenantId);

    // ============================ 块 ③ 有机证书一览（果蔬证书口径） ============================

    /**
     * 最新一张果蔬有机证书的到期日 + 到期天数。
     *
     * <p>「最新证书」= 有效期最晚（{@code MAX(crop_cert_valid)}）的一张。</p>
     * <ul>
     *   <li>{@code certDate} = {@code MAX(crop_cert_valid)}（{@code YYYY-MM-DD}）；无证书 null。</li>
     *   <li>{@code daysToExpiry} = {@code DATEDIFF(MAX(crop_cert_valid), CURDATE())}（可为负）；无证书 null。</li>
     * </ul>
     *
     * @param tenantId 租户
     * @return 单行 {certDate, daysToExpiry}；无在册果蔬证书时两值均 null
     */
    @Select("SELECT MAX(crop_cert_valid)                          AS certDate, "
        + "       DATEDIFF(MAX(crop_cert_valid), CURDATE())     AS daysToExpiry "
        + "  FROM t_plant_crop_organic "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0'")
    CropCertLatestRow selectLatestCropCert(@Param("tenantId") String tenantId);

    /**
     * 在种作物中「在」最新一张果蔬证书覆盖品类（{@code crop_id}）里的不重复作物数。
     *
     * <p>在种作物 = {@code t_plant_plant_details} 进行中（{@code plant_status <> 'completed'}）的
     * 不重复 {@code crop_id}；最新证书 = {@code crop_cert_valid} 最晚的一张，其 {@code crop_id}
     * 为覆盖品类（一证一作物）。</p>
     *
     * @param tenantId 租户
     * @return 在最新证书覆盖品类里的在种作物数，无则 0
     */
    @Select("SELECT COUNT(DISTINCT d.crop_id) "
        + "  FROM t_plant_plant_details d "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND d.plant_status <> 'completed' "
        + "   AND d.crop_id = ( "
        + "       SELECT o.crop_id FROM t_plant_crop_organic o "
        + "        WHERE o.tenant_id = #{tenantId} "
        + "          AND o.del_flag = '0' "
        + "        ORDER BY o.crop_cert_valid DESC, o.id DESC "
        + "        LIMIT 1)")
    Integer selectInLatestCertCropCount(@Param("tenantId") String tenantId);

    /**
     * 在种作物（{@code plant_status <> 'completed'}）的不重复 {@code crop_id} 数。
     *
     * <p>「不在」最新证书覆盖品类的在种作物数 = 本总数 − {@link #selectInLatestCertCropCount}
     * （service 层相减；无证书时 inCert=0 → 全部计入无证书品类）。</p>
     *
     * @param tenantId 租户
     * @return 在种作物不重复品类数，无则 0
     */
    @Select("SELECT COUNT(DISTINCT d.crop_id) "
        + "  FROM t_plant_plant_details d "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND d.plant_status <> 'completed'")
    Integer selectActiveCropCount(@Param("tenantId") String tenantId);

    // ============================ 块 ⑤ 种植甘特（按 作物 维度聚合） ============================

    /**
     * 种植甘特按作物聚合行（GROUP BY crop_id）。
     *
     * <p>每个作物一条种植整段，y 轴类目 = 作物名：</p>
     * <ul>
     *   <li>开始 = {@code MIN(实际开始 / 计划开始旬日兜底)}——计划开始旬日由 {@code plant_month} +
     *       {@code plant_period}（05/15/25 旬 → 当年 1/11/21 号）在 SQL 内推算，无 begin_actualdate
     *       时用它兜底，与原"明细维度"service 推算口径一致。</li>
     *   <li>结束 = {@code MAX(COALESCE(end_actualdate, earliest_harvestdate))}——未结束明细用计划
     *       结束日（{@code earliest_harvestdate}）兜底。</li>
     *   <li>进度 = 该作物种植完成明细占比（{@code plant_status='completed'} 的明细数 / 该作物明细总数 × 100）。</li>
     * </ul>
     *
     * @param tenantId 租户
     * @return 种植甘特聚合行（按作物，最多 100 行），无则空列表
     */
    @Select("SELECT d.crop_id                                                          AS cropId, "
        + "       MAX(c.crop_name)                                                   AS cropName, "
        + "       MIN(COALESCE(d.begin_actualdate, "
        + "           CASE WHEN d.plant_month BETWEEN 1 AND 12 THEN "
        + "                STR_TO_DATE(CONCAT(YEAR(CURDATE()), '-', LPAD(d.plant_month, 2, '0'), '-', "
        + "                    CASE d.plant_period WHEN '15' THEN '11' WHEN '25' THEN '21' ELSE '01' END), "
        + "                    '%Y-%m-%d') "
        + "                ELSE NULL END))                                            AS startDate, "
        + "       MAX(COALESCE(d.end_actualdate, d.earliest_harvestdate))            AS endDate, "
        + "       COUNT(*)                                                           AS totalCount, "
        + "       SUM(CASE WHEN d.plant_status = 'completed' THEN 1 ELSE 0 END)      AS completedCount "
        + "  FROM t_plant_plant_details d "
        + "  LEFT JOIN t_plant_crop_info c ON c.id = d.crop_id AND c.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + " GROUP BY d.crop_id "
        + " ORDER BY MIN(d.begin_actualdate) "
        + " LIMIT 100")
    List<PlantGanttCropRow> selectPlantGanttByCrop(@Param("tenantId") String tenantId);

    // ============================ 块 ⑥ 采摘甘特（按 作物 维度聚合） ============================

    /**
     * 采摘甘特按作物聚合行（GROUP BY crop_id）。
     *
     * <p>每个作物一条采摘整段：开始 = {@code MIN(begin_harvestdate)}、结束 = {@code MAX(end_harvestdate)}；
     * 进度 = 该作物已采摘完成明细占比（{@code harvest_status='completed'} 的明细数 / 该作物有采摘日期的
     * 明细数 × 100）。只统计至少有一段采摘日期（{@code begin_harvestdate} 非 null）的作物。</p>
     *
     * @param tenantId 租户
     * @return 采摘甘特聚合行（按作物，最多 100 行），无则空列表
     */
    @Select("SELECT d.crop_id                                                 AS cropId, "
        + "       MAX(c.crop_name)                                          AS cropName, "
        + "       MIN(d.begin_harvestdate)                                  AS beginHarvestdate, "
        + "       MAX(d.end_harvestdate)                                    AS endHarvestdate, "
        + "       COUNT(*)                                                  AS totalCount, "
        + "       SUM(CASE WHEN d.harvest_status = 'completed' THEN 1 ELSE 0 END) AS completedCount "
        + "  FROM t_plant_plant_details d "
        + "  LEFT JOIN t_plant_crop_info c ON c.id = d.crop_id AND c.del_flag = '0' "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND d.begin_harvestdate IS NOT NULL "
        + " GROUP BY d.crop_id "
        + " ORDER BY MIN(d.begin_harvestdate) "
        + " LIMIT 100")
    List<PickGanttCropRow> selectPickGanttByCrop(@Param("tenantId") String tenantId);

    /**
     * 最新果蔬证书到期日 + 到期天数载体（mapper 内部用）。
     */
    @lombok.Data
    class CropCertLatestRow {

        /** 最新果蔬证书到期日（YYYY-MM-DD，无证书 null）。 */
        private java.time.LocalDate certDate;

        /** 最新果蔬证书到期天数（DATEDIFF，可为负；无证书 null）。 */
        private Integer daysToExpiry;

    }

    /**
     * 种植甘特按作物聚合行（mapper 内部用）。
     */
    @lombok.Data
    class PlantGanttCropRow {

        /** 作物 id（拼接前缀后作为甘特条 id）。 */
        private Long cropId;

        /** 作物名称。 */
        private String cropName;

        /** 该作物种植整段开始（MIN(实际开始 / 计划开始旬日兜底)，可空）。 */
        private java.time.LocalDate startDate;

        /** 该作物种植整段结束（MAX(COALESCE(实际结束, 计划最早采摘日))，可空）。 */
        private java.time.LocalDate endDate;

        /** 该作物种植明细总数。 */
        private Integer totalCount;

        /** 该作物种植完成（plant_status='completed'）明细数。 */
        private Integer completedCount;

    }

    /**
     * 采摘甘特按作物聚合行（mapper 内部用）。
     */
    @lombok.Data
    class PickGanttCropRow {

        /** 作物 id（拼接前缀后作为甘特条 id）。 */
        private Long cropId;

        /** 作物名称。 */
        private String cropName;

        /** 该作物采摘整段开始（MIN begin_harvestdate）。 */
        private java.time.LocalDate beginHarvestdate;

        /** 该作物采摘整段结束（MAX end_harvestdate，可空）。 */
        private java.time.LocalDate endHarvestdate;

        /** 该作物有采摘日期的明细总数。 */
        private Integer totalCount;

        /** 该作物已采摘完成（harvest_status='completed'）明细数。 */
        private Integer completedCount;

    }

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

}
