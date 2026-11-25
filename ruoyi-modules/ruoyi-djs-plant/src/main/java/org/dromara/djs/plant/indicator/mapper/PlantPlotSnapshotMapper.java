package org.dromara.djs.plant.indicator.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 在所有读写中显式限定租户，包含片区和种植明细的连接。 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface PlantPlotSnapshotMapper {

    @Insert("""
        INSERT INTO t_plant_plot_snapshot_day (id, tenant_id, snap_date, create_time, update_time)
        VALUES (#{id}, #{tenantId}, #{date}, NOW(), NOW())
        ON DUPLICATE KEY UPDATE update_time = NOW()
        """)
    void lockDay(@Param("id") long id, @Param("tenantId") String tenantId, @Param("date") LocalDate date);

    @Select("""
        SELECT p.id, p.zone_id, p.plot_area, p.plot_status,
               d.crop_id, d.harvest_status, d.last_harvestdate
          FROM t_plant_plot_info p
          JOIN t_plant_plot_zone z ON z.id = p.zone_id AND z.tenant_id = p.tenant_id
               AND z.del_flag = '0' AND z.zone_status = 1
          LEFT JOIN t_plant_plant_details d ON d.plot_id = p.id AND d.tenant_id = p.tenant_id
               AND d.del_flag = '0' AND d.rotated_at IS NULL
               AND p.plot_status IN (2, 3) AND d.plant_status IN ('ongoing', 'completed')
         WHERE p.tenant_id = #{tenantId} AND p.del_flag = '0'
        """)
    List<CurrentPlotRow> selectCurrentPlots(@Param("tenantId") String tenantId);

    @Delete("DELETE FROM t_plant_plot_status_daily WHERE tenant_id = #{tenantId} AND snap_date = #{date}")
    void deleteDay(@Param("tenantId") String tenantId, @Param("date") LocalDate date);

    @Insert("""
        <script>
        INSERT INTO t_plant_plot_status_daily (
            id, tenant_id, snap_date, plot_id, zone_id, zone_enabled, plot_valid, plot_area,
            is_idle, is_in_field, is_pending_rotation, is_overdue, in_field_crop_ids, create_time, update_time
        ) VALUES
        <foreach collection="plots" item="p" separator=",">
            (#{p.id}, #{tenantId}, #{date}, #{p.plotId}, #{p.zoneId}, 1, 1, #{p.area},
             #{p.idle}, #{p.inField}, #{p.pendingRotation}, #{p.overdue}, #{p.cropIds}, NOW(), NOW())
        </foreach>
        </script>
        """)
    void insertPlots(@Param("tenantId") String tenantId, @Param("date") LocalDate date,
                     @Param("plots") List<CapturedPlot> plots);

    // JSON 展开后同一地块有多行。面积与地块数只读第一行，品种在整天范围内去重。
    @Select("""
        SELECT d.snap_date,
               COUNT(CASE WHEN COALESCE(c.position_no, 1) = 1 THEN p.plot_id END) AS total_plot_count,
               COUNT(CASE WHEN p.is_idle = 1 AND COALESCE(c.position_no, 1) = 1 THEN 1 END) AS idle_plot_count,
               COALESCE(SUM(CASE WHEN p.is_idle = 1 AND COALESCE(c.position_no, 1) = 1
                                 THEN p.plot_area ELSE 0 END), 0) AS idle_area,
               COALESCE(SUM(CASE WHEN p.is_in_field = 1 AND COALESCE(c.position_no, 1) = 1
                                 THEN p.plot_area ELSE 0 END), 0) AS in_field_area,
               COUNT(DISTINCT c.crop_id) AS in_field_crop_count,
               COUNT(CASE WHEN p.is_in_field = 1 AND COALESCE(c.position_no, 1) = 1 THEN 1 END) AS in_field_plot_count,
               COUNT(CASE WHEN p.is_overdue = 1 AND COALESCE(c.position_no, 1) = 1 THEN 1 END) AS overdue_harvest_plot_count,
               COUNT(CASE WHEN p.is_pending_rotation = 1 AND COALESCE(c.position_no, 1) = 1 THEN 1 END) AS pending_rotation_plot_count
          FROM t_plant_plot_snapshot_day d
          LEFT JOIN t_plant_plot_status_daily p ON p.tenant_id = d.tenant_id AND p.snap_date = d.snap_date
               AND p.del_flag = '0' AND p.del_unique = 0 AND p.zone_enabled = 1 AND p.plot_valid = 1
          LEFT JOIN JSON_TABLE(p.in_field_crop_ids, '$[*]'
               COLUMNS (position_no FOR ORDINALITY, crop_id BIGINT PATH '$')) c ON TRUE
         WHERE d.tenant_id = #{tenantId} AND d.del_flag = '0' AND d.del_unique = 0
           AND d.snap_date BETWEEN #{from} AND #{to}
         GROUP BY d.snap_date
        """)
    List<Totals> selectTotals(@Param("tenantId") String tenantId, @Param("from") LocalDate from,
                              @Param("to") LocalDate to);

    record CurrentPlotRow(long plotId, long zoneId, BigDecimal area, int plotStatus,
                          Long cropId, String harvestStatus, LocalDate lastHarvestDate) {
    }

    record CapturedPlot(long id, long plotId, long zoneId, BigDecimal area, boolean idle, boolean inField,
                        boolean pendingRotation, boolean overdue, String cropIds) {
    }

    record Totals(LocalDate snapDate, int totalPlotCount, int idlePlotCount, BigDecimal idleArea,
                  BigDecimal inFieldArea, int inFieldCropCount, int inFieldPlotCount,
                  int overdueHarvestPlotCount, int pendingRotationPlotCount) {
    }
}
