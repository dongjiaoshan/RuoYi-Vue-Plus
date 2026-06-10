package org.dromara.djs.plant.plot.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.domain.vo.PlotInfoVo;

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
     * 只统计未删除空地，启用片区（zone_status=1）。返回每行 {@code {zoneId, zoneName, idleCount}}，
     * 按 zone_code 升序。</p>
     *
     * @return 每行 {@code {zoneId, zoneName, idleCount}}
     */
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
           AND z.zone_status = 1
         GROUP BY z.id, z.zone_name, z.zone_code
         ORDER BY z.zone_code ASC
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
}
