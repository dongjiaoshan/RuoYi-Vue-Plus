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

    @Select("""
        SELECT COUNT(DISTINCT CASE WHEN abnormal_type = 'R' THEN pig_id END) AS returnHeat,
               COUNT(DISTINCT CASE WHEN abnormal_type = 'N' THEN pig_id END) AS emptySow,
               COUNT(DISTINCT CASE WHEN abnormal_type = 'A' THEN pig_id END) AS abortion
          FROM t_farm_pig_abnormal
         WHERE tenant_id = #{tenantId} AND del_flag = '0'
           AND abnormal_date >= #{from} AND abnormal_date < #{to}
        """)
    Map<String, Object> dailyAbnormalTotals(String tenantId, LocalDateTime from, LocalDateTime to);

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
     * 场内留种以及可直接关联的外引按猪 ID。历史外引有首耳号时，按入场日和批次连续耳号范围核对。
     * 比较初始 INTRO 耳号可兼容后续改耳号。仍未关联的后备母猪以逐头进入 HB 的 INTRO 流水
     * 和猪档引种类型匹配旧单，按租户、入场日、引种类型汇总后以旧单总头数封顶。
     */
    @Select("""
        WITH reserves AS (
            SELECT s.tenant_id, s.pig_id, p.introduce_type, origin.intro_date,
                   COALESCE(origin.intro_date, p.introduce_date) AS match_date,
                   COALESCE(origin.ear_no, p.ear_no) AS match_ear_no
              FROM t_farm_pig_snapshot s
              JOIN t_farm_pig_info p ON p.tenant_id = s.tenant_id AND p.id = s.pig_id
              LEFT JOIN LATERAL (
                SELECT r.ear_no, DATE(r.change_time) AS intro_date
                  FROM t_farm_status_record r
                 WHERE r.tenant_id = s.tenant_id AND r.pig_id = s.pig_id
                   AND r.event_type = 'INTRO' AND r.old_status IS NULL AND r.new_status = 'HB'
                 ORDER BY r.change_time, r.id LIMIT 1
              ) origin ON TRUE
             WHERE s.tenant_id = #{tenantId} AND s.snap_date = #{day}
               AND s.pig_type = 'sow' AND s.current_status = 'HB'
               AND NOT EXISTS (
                 SELECT 1 FROM t_farm_pig_breeding b
                  WHERE b.tenant_id = s.tenant_id AND b.pig_id = s.pig_id AND b.del_flag = '0'
                    AND b.breeding_date < DATE_ADD(#{day}, INTERVAL 1 DAY)
               )
        ), matches AS (
            SELECT r.tenant_id, r.intro_date, r.introduce_type, EXISTS (
                SELECT 1 FROM t_farm_pig_introduce i
                 WHERE i.tenant_id = r.tenant_id AND i.del_flag = '0' AND i.introduce_date <= #{day}
                   AND i.introduce_type IN ('internal', 'external')
                   AND (i.pig_id = r.pig_id
                        OR i.pig_id IS NULL AND i.introduce_type = r.introduce_type
                           AND i.introduce_date = r.match_date
                           AND (i.start_ear_no = r.match_ear_no
                                OR (i.pig_count > 1
                                    AND i.start_ear_no REGEXP '-[0-9]{3,}$'
                                    AND r.match_ear_no REGEXP '-[0-9]{3,}$'
                                    AND REGEXP_REPLACE(i.start_ear_no, '[0-9]+$', '') =
                                        REGEXP_REPLACE(r.match_ear_no, '[0-9]+$', '')
                                    AND CAST(SUBSTRING_INDEX(r.match_ear_no, '-', -1) AS UNSIGNED)
                                        BETWEEN CAST(SUBSTRING_INDEX(i.start_ear_no, '-', -1) AS UNSIGNED)
                                        AND CAST(SUBSTRING_INDEX(i.start_ear_no, '-', -1) AS UNSIGNED) + i.pig_count - 1)))
            ) AS linked
              FROM reserves r
        ), unmatched AS (
            SELECT tenant_id, intro_date, introduce_type, COUNT(*) AS candidate_count
              FROM matches
             WHERE NOT linked AND intro_date IS NOT NULL
             GROUP BY tenant_id, intro_date, introduce_type
        ), legacy_orders AS (
            SELECT i.tenant_id, i.introduce_date, i.introduce_type, SUM(i.pig_count) AS order_count
              FROM t_farm_pig_introduce i
             WHERE i.tenant_id = #{tenantId} AND i.del_flag = '0' AND i.introduce_date <= #{day}
               AND i.introduce_type IN ('internal', 'external')
               AND i.pig_id IS NULL AND i.start_ear_no IS NULL AND i.pig_count > 0
               AND (i.pig_sex = 'F' OR i.pig_sex IS NULL)
             GROUP BY i.tenant_id, i.introduce_date, i.introduce_type
        )
        SELECT (SELECT COUNT(*) FROM matches WHERE linked)
               + COALESCE(SUM(LEAST(u.candidate_count, i.order_count)), 0)
          FROM unmatched u
          JOIN legacy_orders i ON i.tenant_id = u.tenant_id AND i.introduce_date = u.intro_date
                              AND i.introduce_type = u.introduce_type
        """)
    int countIntroducedReserve(String tenantId, LocalDate day);

    /** 毛菜间和仓库果蔬实称沿用 kg，仓库饲料物资按商品单位折算。 */
    @Select("""
        WITH pig_feed AS (
            SELECT f.feed_weight, f.crop_id,
                   NULLIF(LOWER(TRIM(REPLACE(p.product_unit, '　', ''))), '') AS product_unit,
                   p.id IS NOT NULL AS has_product,
                   f.feed_type = 'veg_handle'
                     OR (f.feed_type IS NULL AND f.product_id IS NULL AND f.location_id IS NULL) AS from_veg_handle,
                   f.feed_type = 'warehouse' AS from_warehouse,
                   COALESCE(p.belong_type = 'vegetable', FALSE) OR COALESCE(p.buy_class = 'vegetable', FALSE) AS is_vegetable,
                   COALESCE(p.belong_type = 'feed', FALSE) OR COALESCE(p.buy_class = 'feed', FALSE) AS is_feed
              FROM t_warehouse_feed_log f
              LEFT JOIN t_warehouse_product_info p ON p.tenant_id = f.tenant_id AND p.id = f.product_id
             WHERE f.tenant_id = #{tenantId} AND f.del_flag = '0' AND f.feed_animal = 'pig'
               AND f.feed_date >= #{from} AND f.feed_date < #{to}
        ), classified AS (
            SELECT feed_weight, has_product, from_warehouse,
                   CASE
                       WHEN from_veg_handle THEN 1
                       WHEN from_warehouse THEN CASE
                           WHEN product_unit IN ('kg', '千克', '公斤') OR (is_vegetable AND crop_id IS NOT NULL) THEN 1
                           WHEN is_feed AND product_unit IN ('吨', 't') THEN 1000
                           WHEN is_feed AND product_unit = '斤' THEN 0.5
                           WHEN is_feed AND product_unit IN ('克', 'g') THEN 0.001
                       END
                   END AS kg_factor,
                   from_veg_handle OR (from_warehouse AND is_vegetable) AS is_organic
              FROM pig_feed
        )
        SELECT COALESCE(SUM(feed_weight * kg_factor), 0) AS weightKg,
               COALESCE(SUM(CASE WHEN is_organic THEN feed_weight * kg_factor END), 0) AS organicWeightKg,
               COUNT(CASE WHEN from_warehouse AND has_product AND kg_factor IS NULL THEN 1 END) AS nonKgCount
          FROM classified
        """)
    Map<String, Object> feedTotals(String tenantId, LocalDateTime from, LocalDateTime to);

    String DEATH_CULL_EVENTS = """
        SELECT id, pig_id, death_date AS event_at, 'DIE' AS event_type, death_pig_type,
               create_time
          FROM t_farm_pig_death
         WHERE tenant_id = #{tenantId} AND del_flag = '0'
        UNION ALL
        SELECT id, pig_id, culling_date AS event_at, 'ELIMINATE' AS event_type, NULL AS death_pig_type,
               create_time
          FROM t_farm_pig_culling
         WHERE tenant_id = #{tenantId} AND del_flag = '0'
        """;

    /** 先按录入时间及 ID 选最后一次有效操作，再按其业务日期归日。 */
    @Select("""
        WITH events AS (
        """ + DEATH_CULL_EVENTS + """
        ), ranked AS (
            SELECT e.event_at,
                   ROW_NUMBER() OVER (PARTITION BY e.pig_id ORDER BY e.create_time DESC, e.id DESC) AS rn
              FROM events e
        )
        SELECT COUNT(*) FROM ranked WHERE rn = 1 AND event_at >= #{from} AND event_at < #{to}
        """)
    int countDeathCull(String tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 异常优先按单据关联配种，否则取事发时最近有效配种。已分娩的配种剔除，
     * 同一配种的首个有效损失归到其业务日，最后在同一天按母猪去重。
     */
    @Select("""
        WITH terminals AS (
        """ + DEATH_CULL_EVENTS + """
        ), losses AS (
            SELECT a.pig_id, a.abnormal_date AS event_at, a.related_breeding_id
              FROM t_farm_pig_abnormal a
             WHERE a.tenant_id = #{tenantId} AND a.del_flag = '0' AND a.abnormal_type IN ('R', 'N', 'A')
               AND a.abnormal_date < #{to}
            UNION ALL
            SELECT t.pig_id, t.event_at, NULL
              FROM terminals t
              LEFT JOIN t_farm_status_record sr
                ON sr.tenant_id = #{tenantId} AND sr.pig_id = t.pig_id
               AND sr.event_type = t.event_type AND sr.related_event_id = t.id
             WHERE t.event_at < #{to} AND (t.death_pig_type = 'sow' OR t.event_type = 'ELIMINATE')
               AND (sr.id IS NULL OR sr.old_status = 'PZ')
        ), candidates AS (
            SELECT losses.*, MAX(event_at >= #{from}) OVER (PARTITION BY pig_id) AS has_daily_loss
              FROM losses
        ), first_loss AS (
            SELECT b.id AS breeding_id, e.pig_id, MIN(e.event_at) AS event_at
              FROM candidates e
              JOIN LATERAL (
                SELECT b.id, b.breeding_date
                  FROM t_farm_pig_breeding b
                 WHERE b.tenant_id = #{tenantId} AND b.pig_id = e.pig_id AND b.del_flag = '0'
                   AND b.breeding_date <= e.event_at
                   AND (e.related_breeding_id IS NULL OR b.id = e.related_breeding_id)
                 ORDER BY b.breeding_date DESC, b.id DESC LIMIT 1
              ) b ON TRUE
             WHERE e.has_daily_loss = 1 AND NOT EXISTS (
               SELECT 1 FROM t_farm_pig_farrow f
                WHERE f.tenant_id = #{tenantId} AND f.pig_id = e.pig_id AND f.del_flag = '0'
                  AND f.farrow_date >= b.breeding_date AND f.farrow_date <= e.event_at
                  AND (f.breeding_id = b.id OR f.breeding_id IS NULL)
             )
             GROUP BY b.id, e.pig_id
        )
        SELECT COUNT(DISTINCT pig_id) FROM first_loss WHERE event_at >= #{from}
        """)
    int countPregnancyLoss(String tenantId, LocalDateTime from, LocalDateTime to);

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
                OR s.pig_type IN ('fattening', 'piglet') AND s.current_status <> 'END')
        """)
    List<GroupMember> endGroupMembers(String tenantId, LocalDate day);

    @Select("""
        SELECT s.id, s.pig_id AS pigId, s.change_time AS changeTime, s.old_status AS oldStatus,
               s.event_type AS eventType, s.new_status AS newStatus, s.old_pig_type AS oldPigType, s.new_pig_type AS newPigType,
               p.pig_type AS currentPigType, p.wean_date AS weanDate
          FROM t_farm_status_record s
          JOIN t_farm_pig_info p ON p.tenant_id = s.tenant_id AND p.id = s.pig_id AND p.del_flag = '0'
         WHERE s.tenant_id = #{tenantId} AND s.change_time >= #{from} AND s.change_time < #{to}
        """)
    List<GroupChange> groupChanges(String tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 期末和新进育肥共用的归属日期，依次取逐头断奶、猪档断奶、无明细或有匿名明细的整窝断奶。
     * 整窝回退排除断奶日前已离群的仔猪，按单据剩余名额优先分给当天收工仍在群的仔猪。
     * 未分到名额或断奶已撤销的个体可返回空日期，纠正快照的整窝推断或猪档旧值。
     */
    @Select("""
        WITH piglet_tags AS (
            SELECT farrow_id, piglet_ear_no, pig_id
              FROM t_farm_pig_pigletno
             WHERE tenant_id = #{tenantId} AND del_flag = '0' AND pig_id IS NOT NULL
        ), weaning_matches AS (
            SELECT pn.pig_id, w.id AS weaning_id,
                   CASE WHEN w.del_flag = '0' AND d.del_flag = '0' THEN DATE(w.weaning_date) END AS detail_date
              FROM t_farm_pig_weaning w
              JOIN t_farm_pig_weaning_detail d ON d.tenant_id = w.tenant_id AND d.weaning_id = w.id
              JOIN piglet_tags pn ON pn.piglet_ear_no = d.ear_no
                   AND (d.birth_farrow_id IS NULL OR d.birth_farrow_id = pn.farrow_id)
             WHERE w.tenant_id = #{tenantId}
               AND NULLIF(TRIM(d.ear_no), '') IS NOT NULL
        ), individual_dates AS (
            SELECT pig_id, MIN(detail_date) AS detail_date, MAX(detail_date IS NULL) AS has_cancelled_detail
              FROM weaning_matches
             GROUP BY pig_id
        ), matched_counts AS (
            SELECT weaning_id, COUNT(DISTINCT pig_id) AS matched_count
              FROM weaning_matches
             WHERE detail_date IS NOT NULL
             GROUP BY weaning_id
        ), piglet_summaries AS (
            SELECT DISTINCT pn.pig_id, w.id AS weaning_id, DATE(w.weaning_date) AS summary_date,
                   COALESCE(w.weaned_count, 0) - CAST(COALESCE(m.matched_count, 0) AS SIGNED) AS remaining_count,
                   DENSE_RANK() OVER (PARTITION BY pn.pig_id ORDER BY w.weaning_date, w.id) AS summary_rank
              FROM t_farm_pig_weaning w
              JOIN piglet_tags pn ON pn.farrow_id = w.farrow_id
              LEFT JOIN matched_counts m ON m.weaning_id = w.id
             WHERE w.tenant_id = #{tenantId} AND w.del_flag = '0' AND w.weaning_date IS NOT NULL
               AND (EXISTS (
                   SELECT 1 FROM t_farm_pig_weaning_detail d
                    WHERE d.tenant_id = w.tenant_id AND d.weaning_id = w.id AND d.del_flag = '0'
                      AND NULLIF(TRIM(d.ear_no), '') IS NULL
               ) OR (w.weaned_count > 0 AND NOT EXISTS (
                   SELECT 1 FROM t_farm_pig_weaning_detail d
                    WHERE d.tenant_id = w.tenant_id AND d.weaning_id = w.id
               )))
        ), ranked_fallbacks AS (
            SELECT s.pig_id, ROW_NUMBER() OVER (
                       PARTITION BY s.weaning_id ORDER BY (terminal.exited_at IS NOT NULL), s.pig_id
                   ) AS candidate_rank
              FROM piglet_summaries s
              JOIN t_farm_pig_info p ON p.tenant_id = #{tenantId} AND p.id = s.pig_id AND p.del_flag = '0'
              LEFT JOIN individual_dates d ON d.pig_id = s.pig_id
              LEFT JOIN LATERAL (
                  SELECT MIN(e.change_time) AS exited_at
                    FROM t_farm_status_record e
                   WHERE e.tenant_id = p.tenant_id AND e.pig_id = p.id AND e.new_status = 'END'
                     AND e.change_time < DATE_ADD(s.summary_date, INTERVAL 1 DAY)
              ) terminal ON TRUE
             WHERE s.summary_rank = 1 AND d.detail_date IS NULL AND p.wean_date IS NULL
               AND (terminal.exited_at IS NULL OR terminal.exited_at >= s.summary_date)
        )
        SELECT candidate.pigId, candidate.currentPigType, candidate.weanDate
          FROM (
            SELECT p.id AS pigId, p.pig_type AS currentPigType, p.wean_date AS savedDate,
                   CASE WHEN d.detail_date IS NOT NULL THEN d.detail_date
                        WHEN s.weaning_id IS NULL AND d.has_cancelled_detail = 1 THEN NULL
                        ELSE COALESCE(p.wean_date, CASE WHEN r.candidate_rank <= s.remaining_count
                                                        THEN s.summary_date END)
                   END AS weanDate,
                   r.candidate_rank > s.remaining_count AS unassignedFallback
              FROM t_farm_pig_info p
              LEFT JOIN individual_dates d ON d.pig_id = p.id
              LEFT JOIN piglet_summaries s ON s.pig_id = p.id AND s.summary_rank = 1
              LEFT JOIN ranked_fallbacks r ON r.pig_id = p.id
             WHERE p.tenant_id = #{tenantId} AND p.del_flag = '0'
          ) candidate
         WHERE candidate.weanDate = #{day}
            OR NOT (candidate.weanDate <=> candidate.savedDate)
            OR candidate.unassignedFallback
        """)
    List<IndividualWeaning> individualWeanings(String tenantId, LocalDate day);

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

    record GroupChange(long id, long pigId, LocalDateTime changeTime, String oldStatus, String eventType, String newStatus,
                       String oldPigType, String newPigType, String currentPigType, LocalDate weanDate) {}

    record IndividualWeaning(long pigId, String currentPigType, LocalDate weanDate) {}

    record TypeChange(long id, long pigId, LocalDateTime changeTime, String oldPigType, String newPigType) {}

    record MarketingEvent(long pigId, long eventId, LocalDateTime changeTime, String oldPigType, String currentPigType,
                          LocalDate weanDate, BigDecimal weight) {}
}
