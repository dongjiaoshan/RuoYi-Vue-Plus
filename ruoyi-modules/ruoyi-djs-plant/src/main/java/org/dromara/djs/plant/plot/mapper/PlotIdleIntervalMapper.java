package org.dromara.djs.plant.plot.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Mapper
public interface PlotIdleIntervalMapper {

    @Select("""
        <script>
        SELECT id, tenant_id, plot_status, del_flag, idle_open_interval_id, idle_last_ended_on
          FROM t_plant_plot_info
         WHERE id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
         ORDER BY id
         FOR UPDATE
        </script>
        """)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<PlotState> lockPlots(@Param("ids") Collection<Long> ids);

    @Insert("""
        INSERT INTO t_plant_plot_idle_interval (
            id, tenant_id, plot_id, idle_started_on, start_source,
            create_dept, create_by, create_time, update_by, update_time
        ) VALUES (
            #{id}, #{plot.tenantId}, #{plot.id}, #{day}, #{source},
            #{deptId}, #{userId}, NOW(), #{userId}, NOW()
        )
        """)
    void open(@Param("id") long id, @Param("plot") PlotState plot, @Param("day") LocalDate day,
              @Param("source") String source, @Param("deptId") Long deptId, @Param("userId") Long userId);

    @Update("""
        UPDATE t_plant_plot_info
           SET idle_open_interval_id = #{intervalId}
         WHERE id = #{plotId}
        """)
    void setOpenInterval(@Param("plotId") long plotId, @Param("intervalId") long intervalId);

    @Update("""
        UPDATE t_plant_plot_idle_interval i
          JOIN t_plant_plot_info p ON p.id = i.plot_id
           SET i.idle_ended_on = GREATEST(i.idle_started_on, #{day}), i.end_source = #{source},
               i.update_by = #{userId}, i.update_time = NOW(),
               p.idle_open_interval_id = NULL,
               p.idle_last_ended_on = GREATEST(i.idle_started_on, #{day})
         WHERE i.id = #{plot.idleOpenIntervalId} AND p.id = #{plot.id}
           AND i.tenant_id = #{plot.tenantId} AND p.tenant_id = #{plot.tenantId}
           AND i.idle_ended_on IS NULL AND i.del_flag = '0' AND i.del_unique = 0
        """)
    void close(@Param("plot") PlotState plot, @Param("day") LocalDate day, @Param("source") String source,
               @Param("userId") Long userId);

    record PlotState(long id, String tenantId, Integer status, String delFlag,
                     Long idleOpenIntervalId, LocalDate idleLastEndedOn) {
    }
}
