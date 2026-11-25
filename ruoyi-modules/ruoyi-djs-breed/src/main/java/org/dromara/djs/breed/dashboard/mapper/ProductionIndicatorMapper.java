package org.dromara.djs.breed.dashboard.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 生产指标 V1.1 的业务事件取数。每个查询和关联均明确限定租户。 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface ProductionIndicatorMapper {

    @Select("""
        SELECT CASE WHEN COUNT(*) = 0 THEN 0 ELSE SUM(dead_born) END AS deadBorn,
               CASE WHEN COUNT(*) = 0 THEN 0 ELSE SUM(mummy_born) END AS mummyBorn,
               CASE WHEN COUNT(*) = 0 THEN 0 ELSE SUM(deformed_born) END AS deformedBorn,
               CASE WHEN COUNT(*) = 0 THEN 0 ELSE SUM(weak_culled) END AS weakCulled,
               COUNT(DISTINCT DATE(farrow_date), pig_id) AS farrowSows,
               COUNT(DISTINCT CASE WHEN parity = 1 THEN pig_id END) AS firstParity
          FROM t_farm_pig_farrow
         WHERE tenant_id = #{tenantId} AND del_flag = '0'
           AND farrow_date >= #{from} AND farrow_date < #{to}
        """)
    Map<String, Object> farrowTotals(String tenantId, LocalDateTime from, LocalDateTime to);

    @Select("""
        SELECT COUNT(CASE WHEN abnormal_type = 'R' THEN 1 END) AS returnHeat,
               COUNT(CASE WHEN abnormal_type = 'N' THEN 1 END) AS emptySow,
               COUNT(CASE WHEN abnormal_type = 'A' THEN 1 END) AS abortion
          FROM t_farm_pig_abnormal
         WHERE tenant_id = #{tenantId} AND del_flag = '0'
           AND abnormal_date >= #{from} AND abnormal_date < #{to}
        """)
    Map<String, Object> abnormalTotals(String tenantId, LocalDateTime from, LocalDateTime to);

    /** 先从有效配种单确定个体的首配，再检查该次状态日志是否从后备进入已配。 */
    @Select("""
        SELECT COUNT(DISTINCT b.pig_id)
          FROM (
            SELECT id, tenant_id, pig_id, breeding_date,
                   ROW_NUMBER() OVER (PARTITION BY pig_id ORDER BY breeding_date, id) AS rn
              FROM t_farm_pig_breeding
             WHERE tenant_id = #{tenantId} AND del_flag = '0' AND breeding_date < #{to}
          ) b
          JOIN t_farm_status_record s
            ON s.tenant_id = b.tenant_id AND s.pig_id = b.pig_id AND s.related_event_id = b.id
           AND s.event_type = 'BREED' AND s.old_status = 'HB' AND s.new_status = 'PZ'
         WHERE b.rn = 1 AND b.breeding_date >= #{from}
        """)
    int countFirstReserveBreeding(String tenantId, LocalDateTime from, LocalDateTime to);

    /** 逐头资料先取出生日期，再取能确认的出生窝日期。有效重量独立于日龄覆盖。 */
    @Select("""
        SELECT COALESCE(SUM(CASE WHEN age_days >= 0 THEN age_days END), 0) AS ageDaysSum,
               COUNT(CASE WHEN age_days >= 0 THEN 1 END) AS ageValidCount,
               COALESCE(SUM(CASE WHEN weight > 0 THEN weight END), 0) AS weightSum,
               COUNT(CASE WHEN weight > 0 THEN 1 END) AS weightValidCount
          FROM (
            SELECT DATEDIFF(w.weaning_date, COALESCE(p.birth_date, f.farrow_date)) AS age_days, d.weight
              FROM t_farm_pig_weaning w
              JOIN t_farm_pig_weaning_detail d
                ON d.tenant_id = w.tenant_id AND d.weaning_id = w.id AND d.del_flag = '0'
              LEFT JOIN t_farm_pig_pigletno pn
                ON pn.tenant_id = w.tenant_id AND pn.piglet_ear_no = NULLIF(d.ear_no, '') AND pn.del_flag = '0'
               AND (d.birth_farrow_id IS NULL OR pn.farrow_id = d.birth_farrow_id)
              LEFT JOIN t_farm_pig_info p ON p.tenant_id = w.tenant_id AND p.id = pn.pig_id
              LEFT JOIN t_farm_pig_farrow f
                ON f.tenant_id = w.tenant_id AND f.id = COALESCE(d.birth_farrow_id, pn.farrow_id)
               AND f.del_flag = '0'
             WHERE w.tenant_id = #{tenantId} AND w.del_flag = '0'
               AND w.weaning_date >= #{from} AND w.weaning_date < #{to}
          ) detail
        """)
    Map<String, Object> weanedPigletMeasures(String tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 普通 WEAN 按关联有效断奶单找分娩窝。无关联单时，用这头母猪事件发生前的最近一次有效分娩。
     */
    String WEANED_LITTERS_SQL = """
        SELECT paired.farrow_id AS farrowId, DATE(paired.farrow_date) AS farrowDate,
               DATE(MIN(paired.change_time)) AS weanDate
          FROM (
            SELECT s.change_time, f.id AS farrow_id, f.farrow_date
              FROM t_farm_status_record s
              LEFT JOIN t_farm_pig_weaning w
                ON w.tenant_id = s.tenant_id AND w.pig_id = s.pig_id
               AND w.id = s.related_event_id AND w.del_flag = '0'
              JOIN LATERAL (
                SELECT f.id, f.farrow_date
                  FROM t_farm_pig_farrow f
                 WHERE f.tenant_id = s.tenant_id AND f.pig_id = s.pig_id AND f.del_flag = '0'
                   AND f.farrow_date <= s.change_time
                   AND (f.id = w.farrow_id OR s.related_event_id IS NULL)
                 ORDER BY f.farrow_date DESC, f.id DESC
                 LIMIT 1
              ) f ON TRUE
             WHERE s.tenant_id = #{tenantId} AND s.event_type = 'WEAN'
               AND s.old_status = 'FM' AND s.new_status = 'DN' AND s.change_time < #{weanTo}
          ) paired
         GROUP BY paired.farrow_id, paired.farrow_date
        """;

    /** 先取每窝最早的 WEAN，再按断奶日归期，避免跨月重复日志再次计数。 */
    @Select(WEANED_LITTERS_SQL + """
        HAVING MIN(paired.change_time) >= #{from}
        """)
    List<WeanedLitter> weanedLitters(String tenantId, LocalDateTime from, LocalDateTime weanTo);

    /** 分娩日归月，并取截至统计收口日、已完成断奶的窝的哺乳天数。 */
    @Select("""
        SELECT COALESCE(SUM(DATEDIFF(weanDate, farrowDate)), 0)
          FROM (
        """ + WEANED_LITTERS_SQL + """
        ) litters
         WHERE farrowDate >= #{from} AND farrowDate < #{to}
        """)
    int sumLactationDaysByFarrow(String tenantId, LocalDateTime from, LocalDateTime to, LocalDateTime weanTo);

    /** 保留本批出生窝和哺乳窝的关系，明细空的汇总单保留一行供回落到主单头数。 */
    @Select("""
        <script>
        SELECT w.farrow_id AS hostFarrowId, d.birth_farrow_id AS birthFarrowId,
               d.id AS detailId, COALESCE(w.weaned_count, 0) AS recordedCount, w.weaning_date AS weaningDate
          FROM t_farm_pig_weaning w
          LEFT JOIN t_farm_pig_weaning_detail d
            ON d.tenant_id = w.tenant_id AND d.weaning_id = w.id AND d.del_flag = '0'
         WHERE w.tenant_id = #{tenantId} AND w.del_flag = '0' AND w.weaning_date &lt; #{asOf}
           AND (w.farrow_id IN
               <foreach collection="farrowIds" item="id" open="(" close=")" separator=",">#{id}</foreach>
                OR d.birth_farrow_id IN
               <foreach collection="farrowIds" item="id" open="(" close=")" separator=",">#{id}</foreach>)
        </script>
        """)
    List<WeaningBatchRow> weaningBatches(@Param("tenantId") String tenantId, @Param("farrowIds") Collection<Long> farrowIds,
                                         @Param("asOf") LocalDateTime asOf);

    /**
     * 从日末在群快照取在场猪只。个体断奶日修正历史快照按全窝首批日期推断的育肥类型，
     * 已明确发生的转育肥优先使用日志类型。
     */
    @Select("""
        SELECT s.pig_id AS pigId, s.pig_type AS pigType, p.pig_type AS currentPigType, p.wean_date AS weanDate
          FROM t_farm_pig_snapshot s
          LEFT JOIN t_farm_pig_info p ON p.tenant_id = s.tenant_id AND p.id = s.pig_id
         WHERE s.tenant_id = #{tenantId} AND s.snap_date = #{day}
           AND (s.pig_type = 'sow' AND s.current_status NOT IN ('', 'HB', 'END')
                OR s.pig_type = 'fattening'
                OR s.pig_type = 'piglet' AND p.wean_date IS NOT NULL)
        """)
    List<GroupMember> endGroupMembers(String tenantId, LocalDate day);

    @Select("""
        SELECT s.id, s.pig_id AS pigId, s.change_time AS changeTime, s.old_status AS oldStatus,
               s.new_status AS newStatus, s.old_pig_type AS oldPigType, s.new_pig_type AS newPigType,
               p.pig_type AS currentPigType, p.wean_date AS weanDate
          FROM t_farm_status_record s
          JOIN t_farm_pig_info p ON p.tenant_id = s.tenant_id AND p.id = s.pig_id AND p.del_flag = '0'
         WHERE s.tenant_id = #{tenantId} AND s.change_time >= #{from} AND s.change_time < #{to}
        """)
    List<GroupChange> groupChanges(String tenantId, LocalDateTime from, LocalDateTime to);

    @Select("""
        <script>
        SELECT s.id, s.pig_id AS pigId, s.change_time AS changeTime,
               s.old_pig_type AS oldPigType, s.new_pig_type AS newPigType
          FROM t_farm_status_record s
         WHERE s.tenant_id = #{tenantId}
           AND (s.old_pig_type IS NOT NULL OR s.new_pig_type IS NOT NULL)
           AND s.pig_id IN
           <foreach collection="pigIds" item="id" open="(" close=")" separator=",">#{id}</foreach>
         ORDER BY s.pig_id, s.change_time, s.id
        </script>
        """)
    List<TypeChange> typeChanges(@Param("tenantId") String tenantId, @Param("pigIds") Collection<Long> pigIds);

    /** 同一张有效出栏单只取一条 SLAUGHTER 日志，按出栏单业务日期归期。 */
    @Select("""
        SELECT pigId, eventId, changeTime, oldPigType, currentPigType, weanDate, weight
          FROM (
            SELECT m.pig_id AS pigId, s.id AS eventId, s.change_time AS changeTime,
                   s.old_pig_type AS oldPigType, p.pig_type AS currentPigType, p.wean_date AS weanDate,
                   m.out_weight AS weight,
                   ROW_NUMBER() OVER (PARTITION BY m.id ORDER BY s.change_time, s.id) AS rn
              FROM t_farm_pig_marketing m
              JOIN t_farm_status_record s
                ON s.tenant_id = m.tenant_id AND s.pig_id = m.pig_id AND s.related_event_id = m.id
               AND s.event_type = 'SLAUGHTER'
              LEFT JOIN t_farm_pig_info p ON p.tenant_id = m.tenant_id AND p.id = m.pig_id
             WHERE m.tenant_id = #{tenantId} AND m.del_flag = '0'
               AND m.marketing_date >= #{from} AND m.marketing_date < #{to}
          ) marketing
         WHERE rn = 1
        """)
    List<MarketingEvent> marketingEvents(String tenantId, LocalDateTime from, LocalDateTime to);

    record WeanedLitter(long farrowId, LocalDate farrowDate, LocalDate weanDate) {}

    record WeaningBatchRow(Long hostFarrowId, Long birthFarrowId, Long detailId, int recordedCount,
                           LocalDateTime weaningDate) {}

    record GroupMember(long pigId, String pigType, String currentPigType, LocalDate weanDate) {}

    record GroupChange(long id, long pigId, LocalDateTime changeTime, String oldStatus, String newStatus,
                       String oldPigType, String newPigType, String currentPigType, LocalDate weanDate) {}

    record TypeChange(long id, long pigId, LocalDateTime changeTime, String oldPigType, String newPigType) {}

    record MarketingEvent(long pigId, long eventId, LocalDateTime changeTime, String oldPigType, String currentPigType,
                          LocalDate weanDate, BigDecimal weight) {}
}
