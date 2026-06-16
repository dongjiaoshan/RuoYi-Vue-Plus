package org.dromara.djs.plant.plot.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.domain.vo.PlotFarmworkRecordVo;
import org.dromara.djs.plant.plot.domain.vo.PlotInfoVo;
import org.dromara.djs.plant.plot.domain.vo.PlotOrganicRefVo;
import org.dromara.djs.plant.plot.domain.vo.PlotPlantingRecordVo;

import java.util.List;
import java.util.Map;

/**
 * 地块 Mapper（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
public interface PlotInfoMapper extends BaseMapperPlus<PlotInfo, PlotInfoVo> {

    /**
     * 按片区聚合「空地（plot_status=1）」地块数（FIX-PLT-MP-TILL-001 P6 翻耕筛选胶囊）。
     *
     * <p>LEFT JOIN 片区表，保证 0 空地的启用片区也出 {@code X区(0)} 胶囊。
     * 只统计未删除空地，启用片区（zone_status=0，字典 sys_normal_disable：0=正常/启用，1=停用，
     * 与 admin 片区列表 toggle 同口径）。返回每行 {@code {zoneId, zoneName, idleCount}}，
     * 按片区名升序。</p>
     *
     * <p>SQL 已手写完整 {@code tenant_id = '1001'}（与全库 V1 单租户口径一致），用
     * {@code @InterceptorIgnore(tenantLine = "true")} 关掉 MP 租户拦截器对这条手写 LEFT JOIN
     * 的二次注入——否则拦截器会把从表 {@code p} 的租户条件推进 WHERE，令 LEFT JOIN 退化为
     * INNER JOIN、{@code COUNT(p.id)} 落空致 idleCount 全 0。仅作用于本方法，不影响 mapper
     * 其余 CRUD 的租户隔离。</p>
     *
     * @return 每行 {@code {zoneId, zoneName, idleCount}}
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT z.id AS zoneId, z.zone_name AS zoneName, COUNT(p.id) AS idleCount
          FROM t_plant_plot_zone z
          LEFT JOIN t_plant_plot_info p
            ON p.zone_id = z.id
           AND p.plot_status = 1
           AND p.del_flag = '0'
           AND p.tenant_id = '1001'
         WHERE z.del_flag = '0'
           AND z.tenant_id = '1001'
           AND z.zone_status = 0
         GROUP BY z.id, z.zone_name
         ORDER BY z.zone_name ASC
        """)
    List<Map<String, Object>> selectIdleZoneCounts();

    /**
     * 某地块最近一条退茬（rotation）农事日期（FIX-PLT-MP-TILL-001 P8 空地日期·派生）。
     *
     * <p>= 地块最近变空闲日期。无退茬记录返 null（service 兜底回退 update_time 或留空），不动 DDL。</p>
     *
     * @param plotId 地块 id
     * @return 最近 rotation farm_date；无记录返 null
     */
    @Select("""
        SELECT MAX(farm_date)
          FROM t_plant_farm_records
         WHERE plot_id = #{plotId}
           AND farm_type = 'rotation'
           AND del_flag = '0'
           AND tenant_id = '1001'
        """)
    java.time.LocalDate selectLatestRotationDate(@Param("plotId") Long plotId);

    /**
     * 批量取多地块最近退茬日（空地日期·派生），一次 GROUP BY 代替 N 次单查（避免远程 RDS N+1）。
     *
     * @param plotIds 地块 id 列表（非空）
     * @return 行 {@code {plot_id, max_date}}；无退茬记录的地块不出行（service 兜底留空）
     */
    @Select("""
        <script>
        SELECT plot_id AS plotId, MAX(farm_date) AS idleDate
          FROM t_plant_farm_records
         WHERE farm_type = 'rotation'
           AND del_flag = '0'
           AND tenant_id = '1001'
           AND plot_id IN
           <foreach collection="plotIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
         GROUP BY plot_id
        </script>
        """)
    List<Map<String, Object>> selectLatestRotationDates(@Param("plotIds") List<Long> plotIds);

    /**
     * 地块详情·种植信息子表（FIX-PLT-AD-DETAIL-001，11 列，按 plotId 透视）。
     *
     * <p>以种植明细 {@code t_plant_plant_details} 为基（一行一明细），JOIN 作物（名 / 码 / 图）
     * + 计划（保活）+ 班组（种植 {@code plant_by} / 采摘 {@code harvest_by} → team_name）。
     * 「预计 / 实际亩产」按 kg 直返（决策#7 子表用 kg）。按种植日期倒序、明细 id 倒序。</p>
     *
     * <p>显式手写 {@code tenant_id='1001'} + {@code del_flag='0'}，用
     * {@code @InterceptorIgnore(tenantLine = "true")} 关 MP 租户拦截器对手写 LEFT JOIN 的二次注入
     * （否则从表租户条件推进 WHERE 令 LEFT JOIN 退化 INNER JOIN）。仅作用于本方法。</p>
     *
     * @param plotId 地块 id
     * @return 该地块种植记录子表行（无记录返空列表）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT
            d.id                  AS id,
            d.begin_actualdate    AS plantDate,
            COALESCE(c.image_oss_id, c.crop_image_preview) AS cropImage,
            c.crop_name           AS cropName,
            c.crop_code           AS cropCode,
            tp.team_name          AS plantByName,
            d.expected_yield      AS expectedYield,
            d.earliest_harvestdate AS earliestHarvestdate,
            d.last_harvestdate    AS lastHarvestdate,
            d.actual_yield        AS actualYield,
            d.begin_harvestdate   AS beginHarvestdate,
            d.end_harvestdate     AS endHarvestdate,
            th.team_name          AS harvestByName
          FROM t_plant_plant_details d
          LEFT JOIN t_plant_plant_plan p
            ON p.id = d.plant_id
           AND p.del_flag = '0'
          LEFT JOIN t_plant_crop_info c
            ON c.id = d.crop_id
           AND c.del_flag = '0'
          LEFT JOIN t_plant_work_team tp
            ON tp.id = d.plant_by
           AND tp.del_flag = '0'
          LEFT JOIN t_plant_work_team th
            ON th.id = d.harvest_by
           AND th.del_flag = '0'
         WHERE d.plot_id = #{plotId}
           AND d.del_flag = '0'
           AND d.tenant_id = '1001'
         ORDER BY d.begin_actualdate DESC, d.id DESC
        """)
    List<PlotPlantingRecordVo> selectPlantingByPlot(@Param("plotId") Long plotId);

    /**
     * 地块详情·农事信息子表（FIX-PLT-AD-DETAIL-001，按 plotId 透视）。
     *
     * <p>以农事记录 {@code t_plant_farm_records} 为基，JOIN 地块（作业地块名）+ 班组
     * （{@code farm_by} → team_name）。农事类型返字典 key（前端 dict-tag 翻译）。按农事日期倒序。</p>
     *
     * @param plotId 地块 id
     * @return 该地块农事记录子表行（无记录返空列表）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT
            r.id          AS id,
            r.farm_date   AS farmDate,
            r.farm_type   AS farmType,
            pl.plot_name  AS plotName,
            tf.team_name  AS farmByName,
            r.remark      AS remark
          FROM t_plant_farm_records r
          LEFT JOIN t_plant_plot_info pl
            ON pl.id = r.plot_id
           AND pl.del_flag = '0'
          LEFT JOIN t_plant_work_team tf
            ON tf.id = r.farm_by
           AND tf.del_flag = '0'
         WHERE r.plot_id = #{plotId}
           AND r.del_flag = '0'
           AND r.tenant_id = '1001'
         ORDER BY r.farm_date DESC, r.id DESC
        """)
    List<PlotFarmworkRecordVo> selectFarmworkByPlot(@Param("plotId") Long plotId);

    /**
     * 地块详情·认证信息子表（FIX-PLT-AD-DETAIL-001，按 plotId 透视）。
     *
     * <p>以关联表 {@code t_plant_organic_plotno op} 为基 JOIN 证书 {@code t_plant_plot_organic o}，
     * 取该地块关联的有机证书（证书编号 / 颁发单位 / 有效期 / 预警）。按有效期升序（最近到期在前）。</p>
     *
     * @param plotId 地块 id
     * @return 该地块关联有机证书子表行（无记录返空列表）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT
            o.id              AS organicId,
            o.organic_no      AS organicNo,
            o.organic_company AS organicCompany,
            o.organic_valid   AS organicValid,
            o.is_warning      AS isWarning
          FROM t_plant_organic_plotno op
          JOIN t_plant_plot_organic o
            ON o.id = op.organic_id
           AND o.del_flag = '0'
         WHERE op.plot_id = #{plotId}
           AND op.del_flag = '0'
           AND op.tenant_id = '1001'
         ORDER BY o.organic_valid ASC, o.id DESC
        """)
    List<PlotOrganicRefVo> selectOrganicByPlot(@Param("plotId") Long plotId);
}
