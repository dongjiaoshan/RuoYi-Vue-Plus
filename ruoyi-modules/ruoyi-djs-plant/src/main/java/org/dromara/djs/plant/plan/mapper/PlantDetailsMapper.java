package org.dromara.djs.plant.plan.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.domain.vo.PlantCropTaskVo;
import org.dromara.djs.plant.plan.domain.vo.PlantDetailsVo;
import org.dromara.djs.plant.plan.domain.vo.PlantMonthTaskVo;
import org.dromara.djs.plant.plan.domain.vo.PlotYearPlanRowVo;
import org.dromara.djs.plant.plan.domain.vo.SeedSummaryVo;

import java.util.List;
import java.util.Map;

/**
 * 种植计划明细 Mapper（PLT-PLAN-001）。
 *
 * @author djs
 * @since PLT-PLAN-001
 */
public interface PlantDetailsMapper extends BaseMapperPlus<PlantDetails, PlantDetailsVo> {

    /**
     * 当月播种任务「明细级」列表（FIX-PLANT-SEED-001，mp 作物种植详情页逐块开工用）。
     *
     * <p>D2「拆两接口」的明细侧：一行一明细，保留 {@code id}（明细 id）+ {@code beginActualdate}，
     * 供详情页逐块开工（{@code startPlant({ detailIds:[p.id] })}）。首页作物卡 + 「全部(N)」计数
     * + 完成率走另一接口 {@link #selectCropTasks}（作物级聚合）。</p>
     *
     * <p>以当月种植明细为基，JOIN 地块（片区 / 面积）+ 作物（名 / 图）+ 计划（单号 / 自由文本日期），
     * 按片区 + 地块码排序。</p>
     *
     * <p>"当月" = {@code plant_month = #{month}}（明细表存 1-12 月份，非具体日期）。
     * 租户隔离：V1 单租户显式 {@code tenant_id='1001'}（未启全局 MP 拦截器，对齐
     * {@link PlantPlanMapper#selectDemandSummary}）。</p>
     *
     * @param month 当前月份 1-12
     * @return 当月播种任务明细行列表（无数据返空列表）
     */
    @Select("""
        SELECT
            d.id            AS id,
            d.plant_id      AS plantId,
            p.plan_no       AS planNo,
            d.plot_id       AS plotId,
            pl.plot_code    AS plotCode,
            pl.plot_name    AS plotName,
            pl.zone_id      AS zoneId,
            z.zone_name     AS zoneName,
            d.crop_id       AS cropId,
            c.crop_name     AS cropName,
            COALESCE(NULLIF(SUBSTRING_INDEX(c.crop_image_url, ',', 1), ''), c.image_oss_id) AS cropImg,
            d.plot_area     AS area,
            d.plant_month   AS plantMonth,
            d.begin_actualdate AS beginActualdate,
            d.end_actualdate AS endActualdate,
            d.plant_period  AS plantPeriod,
            p.plant_date    AS planDate,
            d.expected_yield AS expectedYield,
            d.actual_yield  AS actualYield,
            d.plant_status  AS plantStatus
          FROM t_plant_plant_details d
          JOIN t_plant_plant_plan p
            ON p.id = d.plant_id
           AND p.del_flag = '0'
           AND p.tenant_id = d.tenant_id
          LEFT JOIN t_plant_plot_info pl
            ON pl.id = d.plot_id
           AND pl.del_flag = '0'
          LEFT JOIN t_plant_plot_zone z
            ON z.id = pl.zone_id
           AND z.del_flag = '0'
          LEFT JOIN t_plant_crop_info c
            ON c.id = d.crop_id
           AND c.del_flag = '0'
         WHERE d.del_flag = '0'
           AND d.tenant_id = '1001'
           AND d.plant_month = #{month}
         ORDER BY z.zone_name ASC, pl.plot_code ASC, d.id ASC
        """)
    List<PlantMonthTaskVo> selectMonthTasks(@Param("month") Integer month);

    /**
     * 当月播种任务「作物级聚合」列表（FIX-PLT-MP-SEED-002，mp 播种首页作物卡）。
     *
     * <p>D2「拆两接口」的聚合侧：以当月明细按 {@code crop_id} GROUP BY，喂首页作物卡
     * + 「全部(N)」计数（= 作物数 = 返回行数）+ 完成率。逐块开工走 {@link #selectMonthTasks}。</p>
     *
     * <p>完成率口径（D1 = 开工进度）：{@code startedPlotCount / plotCount}，前端按两值算避免除零。
     * 一作物多地块时，{@code plotCount} = 明细行数（一明细一地块一行），
     * {@code startedPlotCount} = {@code begin_actualdate IS NOT NULL} 行数。</p>
     *
     * <p>展示字段取每组首行（按片区 + 地块码 + 明细 id 排序后 MIN/MAX 聚合）：作物图取该组任一非空图、
     * 片区名 {@code GROUP_CONCAT DISTINCT} 拼接、月份取 MIN、阶段/自由文本取该最早月份组内任一值。</p>
     *
     * <p>"当月" = {@code plant_month = #{month}}；租户隔离 V1 单租户显式 {@code tenant_id='1001'}。</p>
     *
     * @param month 当前月份 1-12
     * @return 当月播种作物聚合卡列表（无数据返空列表，行数 = 当月种植作物数）
     */
    @Select("""
        SELECT
            d.crop_id                       AS cropId,
            MAX(c.crop_name)                AS cropName,
            MAX(COALESCE(NULLIF(SUBSTRING_INDEX(c.crop_image_url, ',', 1), ''), c.image_oss_id)) AS cropImg,
            GROUP_CONCAT(DISTINCT z.zone_name ORDER BY z.zone_name SEPARATOR '、') AS zoneName,
            COALESCE(SUM(d.plot_area), 0)   AS totalArea,
            COUNT(*)                        AS plotCount,
            SUM(CASE WHEN d.begin_actualdate IS NOT NULL THEN 1 ELSE 0 END) AS startedPlotCount,
            MIN(d.plant_month)              AS plantMonth,
            MIN(d.plant_period)             AS plantPeriod,
            MAX(p.plant_date)               AS planDate
          FROM t_plant_plant_details d
          JOIN t_plant_plant_plan p
            ON p.id = d.plant_id
           AND p.del_flag = '0'
           AND p.tenant_id = d.tenant_id
          LEFT JOIN t_plant_plot_info pl
            ON pl.id = d.plot_id
           AND pl.del_flag = '0'
          LEFT JOIN t_plant_plot_zone z
            ON z.id = pl.zone_id
           AND z.del_flag = '0'
          LEFT JOIN t_plant_crop_info c
            ON c.id = d.crop_id
           AND c.del_flag = '0'
         WHERE d.del_flag = '0'
           AND d.tenant_id = '1001'
           AND d.plant_month = #{month}
         GROUP BY d.crop_id
         ORDER BY MAX(c.crop_name) ASC, d.crop_id ASC
        """)
    List<PlantCropTaskVo> selectCropTasks(@Param("month") Integer month);

    /**
     * mp 播种首页 3 KPI 聚合（FIX-PLT-MP-SEED-001 #6.5）：当月完成率 / 当日种植品种数 / 当日种植地块数。
     *
     * <p>口径：</p>
     * <ul>
     *   <li>{@code monthCompletionRate} = ROUND(当月已完成种植明细数 / 当月明细总数 × 100)，
     *       当月无明细时 COALESCE 兜底返 0（不除零）；"当月" = {@code plant_month=#{month}}。
     *       完成口径 = {@code plant_status='completed'}（取消开工分步后只有「待种植 / 已完成种植」两态）。</li>
     *   <li>{@code todayCropKindCount} = {@code begin_actualdate=#{today}} 明细 distinct crop_id 数（种植日期=今日）。</li>
     *   <li>{@code todayPlotCount} = {@code begin_actualdate=#{today}} 明细 distinct plot_id 数（种植日期=今日）。</li>
     * </ul>
     *
     * <p>只读聚合，显式 {@code tenant_id='1001'} + {@code del_flag='0'}（对齐 {@link #selectMonthTasks}）。</p>
     *
     * @param month 当月 1-12
     * @param today 今日（yyyy-MM-dd）
     * @return 聚合 VO（永不返 null 行，各字段 COALESCE 兜底 0）
     */
    @Select("""
        SELECT
            COALESCE(ROUND(
                SUM(CASE WHEN plant_month = #{month} AND plant_status = 'completed' THEN 1 ELSE 0 END)
                / NULLIF(SUM(CASE WHEN plant_month = #{month} THEN 1 ELSE 0 END), 0) * 100
            ), 0) AS monthCompletionRate,
            COALESCE(COUNT(DISTINCT CASE WHEN begin_actualdate = #{today} THEN crop_id END), 0) AS todayCropKindCount,
            COALESCE(COUNT(DISTINCT CASE WHEN begin_actualdate = #{today} THEN plot_id END), 0) AS todayPlotCount
          FROM t_plant_plant_details
         WHERE del_flag = '0'
           AND tenant_id = '1001'
        """)
    SeedSummaryVo selectSeedSummary(@Param("month") Integer month, @Param("today") java.time.LocalDate today);

    /**
     * 向导 step3：批量统计某年份各地块的计划种植次数（轮作次数）（测试反馈 row27 子项1）。
     *
     * <p>"当年该地块计划种植次数" = 该地块在 planYear 下的种植明细行数
     * （JOIN 主表按 {@code p.plan_year=#{planYear}} 过滤）。一次 GROUP BY plot_id 批量返回，避免 N+1。
     * 仅统计入参 plotIds 内的地块；plotIds 为空时外层不调。</p>
     *
     * <p>租户隔离：V1 单租户显式 {@code tenant_id='1001'}，del_flag='0'。
     * 返回 Map 行：{@code plotId(Long) / rotationCount(Long)}，未命中的地块由 service 兜 0。</p>
     *
     * @param plotIds  本次返回的地块 id 列表
     * @param planYear 计划年份
     * @return 每行 plotId → rotationCount（无明细的地块不出现在结果里）
     */
    @Select("""
        <script>
        SELECT
            d.plot_id AS plotId,
            COUNT(*)  AS rotationCount
          FROM t_plant_plant_details d
          JOIN t_plant_plant_plan p
            ON p.id = d.plant_id
           AND p.del_flag = '0'
           AND p.tenant_id = d.tenant_id
           AND p.plan_year = #{planYear}
         WHERE d.del_flag = '0'
           AND d.tenant_id = '1001'
           AND d.plot_id IN
           <foreach collection="plotIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
         GROUP BY d.plot_id
        </script>
        """)
    List<Map<String, Object>> selectRotationCountByYear(@Param("plotIds") List<Long> plotIds,
                                                         @Param("planYear") Integer planYear);

    /**
     * 向导 step3「查看」弹框：某地块在某年份的计划实际数据列表（测试反馈 row27 子项2）。
     *
     * <p>列：计划种植时间（plant_month + plant_period）/ 计划种植作物（crop_name）/
     * 最早采摘日期 / 最晚采摘日期 / 种植状态 / 采摘状态。按月份 + 旬别升序。</p>
     *
     * <p>租户隔离：V1 单租户显式 {@code tenant_id='1001'}，del_flag='0'。</p>
     *
     * @param plotId   地块 id
     * @param planYear 计划年份
     * @return 该地块当年种植明细行列表（无数据返空列表）
     */
    @Select("""
        SELECT
            d.id                  AS detailId,
            d.plant_month         AS plantMonth,
            d.plant_period        AS plantPeriod,
            c.crop_name           AS cropName,
            d.earliest_harvestdate AS earliestHarvestdate,
            d.last_harvestdate    AS lastHarvestdate,
            d.plant_status        AS plantStatus,
            d.harvest_status      AS harvestStatus
          FROM t_plant_plant_details d
          JOIN t_plant_plant_plan p
            ON p.id = d.plant_id
           AND p.del_flag = '0'
           AND p.tenant_id = d.tenant_id
           AND p.plan_year = #{planYear}
          LEFT JOIN t_plant_crop_info c
            ON c.id = d.crop_id
           AND c.del_flag = '0'
         WHERE d.del_flag = '0'
           AND d.tenant_id = '1001'
           AND d.plot_id = #{plotId}
         ORDER BY d.plant_month ASC, d.plant_period ASC, d.id ASC
        """)
    List<PlotYearPlanRowVo> selectPlotYearPlanRows(@Param("plotId") Long plotId,
                                                   @Param("planYear") Integer planYear);
}
