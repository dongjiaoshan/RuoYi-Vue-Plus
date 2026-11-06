package org.dromara.djs.plant.plot.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.plant.plot.domain.vo.PlotPlantingStatVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotCardVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotFarmworkVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotPlantingVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotSummaryVo;

import java.util.Collection;
import java.util.List;

/**
 * mp 管理·地块查询 / 地块详情只读 Mapper（V7-R31 / V7-R32）。
 *
 * <p>手写 SQL 全部显式 {@code tenant_id = '1001'} + {@code del_flag = '0'}，并用
 * {@code @InterceptorIgnore(tenantLine = "true")} 关掉 MP 租户拦截器对手写 LEFT JOIN 的二次注入
 * （否则从表租户条件推进 WHERE，LEFT JOIN 退化为 INNER JOIN）。与 {@link PlotInfoMapper} 同口径。</p>
 *
 * <p>「种植记录」= {@code t_plant_plant_details} 中 {@code begin_actualdate IS NOT NULL} 的行
 * （与 mp planRecords 同源）；未开工的计划明细不算种植。</p>
 *
 * @author djs
 */
@Mapper
public interface AppletPlotQueryMapper {

    /**
     * 地块列表（名称模糊 + 状态精确），按地块编码升序。
     *
     * @param plotName   地块名称关键字（空 = 不过滤）
     * @param plotStatus 地块状态 1/2/3（空 = 全部）
     * @return 地块基础行（当前茬 / 统计字段由 service 回填）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        <script>
        SELECT p.id AS id, p.plot_code AS plotCode, p.plot_name AS plotName,
               p.plot_area AS plotArea, p.plot_status AS plotStatus
          FROM t_plant_plot_info p
         WHERE p.del_flag = '0'
           AND p.tenant_id = '1001'
           <if test="plotName != null and plotName != ''">
           AND p.plot_name LIKE CONCAT('%', #{plotName}, '%')
           </if>
           <if test="plotStatus != null">
           AND p.plot_status = #{plotStatus}
           </if>
         ORDER BY p.plot_code ASC, p.id ASC
        </script>
        """)
    List<AppletPlotCardVo> selectPlots(@Param("plotName") String plotName, @Param("plotStatus") Integer plotStatus);

    /**
     * 地块查询顶部统计：与 {@link #selectPlots} 同一 WHERE（名称模糊 + 状态精确），按 plot_status 计数。
     *
     * @param plotName   地块名称关键字（空 = 不过滤）
     * @param plotStatus 地块状态 1/2/3（空 = 全部）
     * @return 种植数 / 采摘数 / 空地数（无匹配地块时三项均为 0）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        <script>
        SELECT COALESCE(SUM(CASE WHEN p.plot_status = 2 THEN 1 ELSE 0 END), 0) AS plantingCount,
               COALESCE(SUM(CASE WHEN p.plot_status = 3 THEN 1 ELSE 0 END), 0) AS pickingCount,
               COALESCE(SUM(CASE WHEN p.plot_status = 1 THEN 1 ELSE 0 END), 0) AS idleCount
          FROM t_plant_plot_info p
         WHERE p.del_flag = '0'
           AND p.tenant_id = '1001'
           <if test="plotName != null and plotName != ''">
           AND p.plot_name LIKE CONCAT('%', #{plotName}, '%')
           </if>
           <if test="plotStatus != null">
           AND p.plot_status = #{plotStatus}
           </if>
        </script>
        """)
    AppletPlotSummaryVo selectSummary(@Param("plotName") String plotName, @Param("plotStatus") Integer plotStatus);

    /**
     * 单地块基础行。
     *
     * @param plotId 地块 id
     * @return 基础行；不存在 / 已删返 null
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT p.id AS id, p.plot_code AS plotCode, p.plot_name AS plotName,
               p.plot_area AS plotArea, p.plot_status AS plotStatus
          FROM t_plant_plot_info p
         WHERE p.id = #{plotId}
           AND p.del_flag = '0'
           AND p.tenant_id = '1001'
        """)
    AppletPlotCardVo selectPlotById(@Param("plotId") Long plotId);

    /**
     * 批量取若干地块「已开工且未退茬」的种植明细（每地块按种植日期降序、id 降序），service 取每地块首行作当前茬。
     *
     * <p>复用 {@link AppletPlotCardVo} 承载：{@code id} 列放的是地块 id（便于按地块归组），
     * 当前茬字段直接落同名属性。预计产量只扣本茬期间的灾害损失（{@link PlotPlantingSql#NET_EXPECTED_YIELD}），与 admin 地块详情种植子表同口径。</p>
     *
     * @param plotIds 地块 id（非空）
     * @return 候选行
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        <script>
        SELECT d.plot_id AS id, c.crop_name AS cropName,
               d.begin_actualdate AS plantDate, d.begin_harvestdate AS beginHarvestdate,
               d.end_harvestdate AS endHarvestdate,
               """ + PlotPlantingSql.NET_EXPECTED_YIELD + """
               AS expectedYield,
               d.actual_yield AS actualYield
          FROM t_plant_plant_details d
          LEFT JOIN t_plant_crop_info c
            ON c.id = d.crop_id AND c.del_flag = '0'
         WHERE d.del_flag = '0'
           AND d.tenant_id = '1001'
           AND d.begin_actualdate IS NOT NULL
           AND d.rotated_at IS NULL
           AND d.plot_id IN
           <foreach collection="plotIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
         ORDER BY d.plot_id ASC, d.begin_actualdate DESC, d.id DESC
        </script>
        """)
    List<AppletPlotCardVo> selectCurrentCandidates(@Param("plotIds") Collection<Long> plotIds);

    /**
     * 按地块聚合已开工种植明细：历史种植次数 / 最高亩产（kg/亩）/ 亩产最高作物名。
     *
     * <p>最高亩产只看采摘完成（{@code harvest_status='completed'}）且有实际产量、面积 &gt; 0 的行，
     * 亩产 = {@code actual_yield / plot_area}（明细冗余面积）；同值取 id 大者。与 admin 地块列表
     * {@link PlotInfoMapper#selectPlantingStats} 的亩产规则一致，计数只差「未开工计划不算」。</p>
     *
     * @param plotIds 地块 id（非空）
     * @return 聚合行（无种植记录的地块不出行）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        <script>
        SELECT d.plot_id AS plotId, COUNT(*) AS historyPlantCount,
          MAX(CASE WHEN d.harvest_status = 'completed' AND d.actual_yield IS NOT NULL AND d.plot_area &gt; 0
                   THEN d.actual_yield / d.plot_area END) AS maxYieldPerMu,
          (SELECT c.crop_name FROM t_plant_plant_details d2
             JOIN t_plant_crop_info c ON c.id = d2.crop_id AND c.del_flag = '0' AND c.tenant_id = '1001'
            WHERE d2.plot_id = d.plot_id AND d2.del_flag = '0' AND d2.tenant_id = '1001'
              AND d2.begin_actualdate IS NOT NULL
              AND d2.harvest_status = 'completed' AND d2.actual_yield IS NOT NULL AND d2.plot_area &gt; 0
            ORDER BY (d2.actual_yield / d2.plot_area) DESC, d2.id DESC LIMIT 1) AS maxYieldCropName
          FROM t_plant_plant_details d
         WHERE d.tenant_id = '1001' AND d.del_flag = '0'
           AND d.begin_actualdate IS NOT NULL
           AND d.plot_id IN
           <foreach collection="plotIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
         GROUP BY d.plot_id
        </script>
        """)
    List<PlotPlantingStatVo> selectStartedStats(@Param("plotIds") Collection<Long> plotIds);

    /**
     * 地块详情·种植记录（已开工明细，种植日期降序、id 降序）。班组多选走 {@code t_plant_details_team}，
     * 无行时回落单列 {@code plant_by} / {@code harvest_by}（与 admin 地块详情种植子表同口径）。
     *
     * @param plotId 地块 id
     * @return 种植记录（cropImage 暂承载作物 image_oss_id）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT
            d.id                  AS id,
            d.crop_id             AS cropId,
            c.crop_name           AS cropName,
            c.image_oss_id        AS cropImage,
            d.begin_actualdate    AS plantDate,
            d.begin_harvestdate   AS beginHarvestdate,
            d.end_harvestdate     AS endHarvestdate,
            """ + PlotPlantingSql.NET_EXPECTED_YIELD + """
            AS expectedYield,
            d.actual_yield        AS actualYield,
            d.harvest_status      AS harvestStatus,
            d.plot_area           AS plotArea,
            COALESCE((SELECT GROUP_CONCAT(t.team_name ORDER BY t.id SEPARATOR ',')
                        FROM t_plant_details_team dt
                        JOIN t_plant_work_team t ON t.id = dt.team_id AND t.del_flag = '0'
                       WHERE dt.detail_id = d.id AND dt.role = 'plant' AND dt.del_flag = '0'),
                     tp.team_name) AS plantTeamNames,
            COALESCE((SELECT GROUP_CONCAT(t.team_name ORDER BY t.id SEPARATOR ',')
                        FROM t_plant_details_team dt
                        JOIN t_plant_work_team t ON t.id = dt.team_id AND t.del_flag = '0'
                       WHERE dt.detail_id = d.id AND dt.role = 'harvest' AND dt.del_flag = '0'),
                     th.team_name) AS harvestTeamNames
          FROM t_plant_plant_details d
          LEFT JOIN t_plant_crop_info c
            ON c.id = d.crop_id AND c.del_flag = '0'
          LEFT JOIN t_plant_work_team tp
            ON tp.id = d.plant_by AND tp.del_flag = '0'
          LEFT JOIN t_plant_work_team th
            ON th.id = d.harvest_by AND th.del_flag = '0'
         WHERE d.plot_id = #{plotId}
           AND d.del_flag = '0'
           AND d.tenant_id = '1001'
           AND d.begin_actualdate IS NOT NULL
         ORDER BY d.begin_actualdate DESC, d.id DESC
        """)
    List<AppletPlotPlantingVo> selectPlantings(@Param("plotId") Long plotId);

    /**
     * 地块详情·农事记录（农事日期降序、id 降序）。作物名优先取作物档案，回落记录冗余 crop_name。
     *
     * @param plotId 地块 id
     * @return 农事记录（teamNames / farmTypeLabel 由 service 回填）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT
            r.id          AS id,
            r.farm_date   AS farmDate,
            r.farm_type   AS farmType,
            r.plot_type   AS plotType,
            r.crop_id     AS cropId,
            COALESCE(c.crop_name, r.crop_name) AS cropName,
            tf.team_name  AS farmByName
          FROM t_plant_farm_records r
          LEFT JOIN t_plant_crop_info c
            ON c.id = r.crop_id AND c.del_flag = '0'
          LEFT JOIN t_plant_work_team tf
            ON tf.id = r.farm_by AND tf.del_flag = '0'
         WHERE r.plot_id = #{plotId}
           AND r.del_flag = '0'
           AND r.tenant_id = '1001'
         ORDER BY r.farm_date DESC, r.id DESC
        """)
    List<AppletPlotFarmworkVo> selectFarmworks(@Param("plotId") Long plotId);
}
