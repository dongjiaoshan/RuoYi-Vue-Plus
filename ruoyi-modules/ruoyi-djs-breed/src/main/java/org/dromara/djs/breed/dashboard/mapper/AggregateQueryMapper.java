package org.dromara.djs.breed.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 跨表聚合查询 Mapper（BRD-DASH-001）。
 *
 * <p>本 mapper 不绑定单表实体；只提供 dashboard 聚合 service 调用的原始 SQL，
 * 数据源：12 张 event 表 + {@code t_farm_status_record} + {@code t_farm_pig_info}。</p>
 *
 * <p><b>关键约定（ADR-0007 + D6 closing 决策 #8）</b>：
 * 猪只终止日期一律走 {@code t_farm_status_record.change_time WHERE event_type IN ('DIE','ELIMINATE','SLAUGHTER')}，
 * <b>不</b>查 {@code t_farm_pig_info.end_date}（该列不存在）。</p>
 *
 * @author djs
 * @since BRD-DASH-001
 */
@Mapper
public interface AggregateQueryMapper {

    /**
     * 实时库存：按 pig_type 分组 COUNT（排除 lifecycle='END'）。
     *
     * @param tenantId 租户
     * @return 形如 [{pig_type:'sow', cnt:23}, ...]
     */
    @Select("SELECT pig_type AS pigType, COUNT(*) AS cnt "
        + " FROM t_farm_pig_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND current_status <> 'END' "
        + " GROUP BY pig_type")
    List<Map<String, Object>> countInventoryByType(@Param("tenantId") String tenantId);

    /**
     * 按 lifecycle 分组 COUNT 母猪（用于 sow_record.sow_pregnant/farrow/weaning/idle 拆分）。
     *
     * @param tenantId 租户
     * @param pigType  pig_type 过滤（sow/piglet）
     * @return 形如 [{lifecycle:'PZ', cnt:5}, ...]
     */
    @Select("SELECT current_status AS lifecycle, COUNT(*) AS cnt "
        + " FROM t_farm_pig_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND current_status <> 'END' "
        + "   AND pig_type = #{pigType} "
        + " GROUP BY current_status")
    List<Map<String, Object>> countByLifecycle(@Param("tenantId") String tenantId,
                                               @Param("pigType") String pigType);

    /**
     * 按 event_type 统计 status_record 在 [from, to) 区间内的事件数。
     *
     * <p>注意 to 是开区间（exclusive），调用方传入"次日 00:00"语义。</p>
     *
     * @param tenantId 租户
     * @param eventType 事件类型（DIE / ELIMINATE / SLAUGHTER / BREED / FARROW / WEAN / OESTRUS / NULL_RETURN / INTRO / CASTRATE / TRANSFER）
     * @param from 起始时间（含）
     * @param to   结束时间（不含）
     */
    @Select("SELECT COUNT(*) FROM t_farm_status_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND event_type = #{eventType} "
        + "   AND change_time >= #{from} "
        + "   AND change_time <  #{to}")
    int countStatusEventInRange(@Param("tenantId") String tenantId,
                                @Param("eventType") String eventType,
                                @Param("from") java.time.LocalDateTime from,
                                @Param("to") java.time.LocalDateTime to);

    /**
     * t_farm_pig_introduce 在月份内的引种头数（外购 + 内部）。
     */
    @Select("SELECT COALESCE(SUM(pig_count),0) FROM t_farm_pig_introduce "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND introduce_date >= #{from} "
        + "   AND introduce_date <  #{to}")
    int sumIntroducedInRange(@Param("tenantId") String tenantId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to);

    /**
     * t_farm_pig_farrow 在月份内的活产仔数（SUM live_born）。
     * farrow_date 是 datetime，传入 LocalDate 由 MyBatis 强转 00:00 起点。
     */
    @Select("SELECT COALESCE(SUM(live_born),0) FROM t_farm_pig_farrow "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND farrow_date >= #{from} "
        + "   AND farrow_date <  #{to}")
    int sumLiveBornInRange(@Param("tenantId") String tenantId,
                           @Param("from") LocalDate from,
                           @Param("to") LocalDate to);

    /**
     * t_farm_pig_weaning 在月份内的断奶头数（SUM weaned_count）。
     */
    @Select("SELECT COALESCE(SUM(weaned_count),0) FROM t_farm_pig_weaning "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND weaning_date >= #{from} "
        + "   AND weaning_date <  #{to}")
    int sumWeanedInRange(@Param("tenantId") String tenantId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to);

    /**
     * t_farm_pig_marketing 在月份内的出栏头数 + 总重（kg）。
     * 一头猪一行 → cnt = COUNT(*) / weight = SUM(out_weight)。
     *
     * @return {cnt: Long, weight: BigDecimal}
     */
    @Select("SELECT COUNT(*) AS cnt, COALESCE(SUM(out_weight),0) AS weight "
        + " FROM t_farm_pig_marketing "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND marketing_date >= #{from} "
        + "   AND marketing_date <  #{to}")
    Map<String, Object> aggregateMarketingInRange(@Param("tenantId") String tenantId,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);

    /**
     * 当年仍活的母猪平均存栏（用于 PSY = annual_weaned / avg_sow_alive）。
     *
     * <p>近似算法：取当年最后一天 23:59 的活母猪头数（current_status NOT IN END 且 pig_type=sow）。
     * V1 简化，不做月度滑动平均。</p>
     */
    @Select("SELECT COUNT(*) FROM t_farm_pig_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND pig_type = 'sow' "
        + "   AND current_status <> 'END'")
    int countAliveSows(@Param("tenantId") String tenantId);

    // ============================================================
    //  月度活动统计 by-month（BRD-DASH-ACTIVITY-001，原型 21）
    //  各 event 表按 DATE_FORMAT(dateColumn,'%m-%d') 分组聚合，区间 [from, to) 右开。
    //  返回形如 [{d:'05-01', v:3}, ...]；service 按 days 列表对齐补 0。
    //  table / dateColumn / valueColumn 全部白名单内部传入（非用户输入），无注入风险。
    // ============================================================

    /** 业务事件表按日 COUNT(*)（如配种 / 查情 / 分娩 / 断奶头数）。 */
    @Select("<script>"
        + "SELECT DATE_FORMAT(${dateColumn}, '%m-%d') AS d, COUNT(*) AS v "
        + " FROM ${table} "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND ${dateColumn} &gt;= #{from} "
        + "   AND ${dateColumn} &lt;  #{to} "
        + " GROUP BY d"
        + "</script>")
    List<Map<String, Object>> countEventByDay(@Param("table") String table,
                                              @Param("dateColumn") String dateColumn,
                                              @Param("tenantId") String tenantId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    /** 业务事件表按日 SUM(valueColumn)（如引种头数 / 产仔数 / 活仔数 / 打标数）。 */
    @Select("<script>"
        + "SELECT DATE_FORMAT(${dateColumn}, '%m-%d') AS d, COALESCE(SUM(${valueColumn}),0) AS v "
        + " FROM ${table} "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND ${dateColumn} &gt;= #{from} "
        + "   AND ${dateColumn} &lt;  #{to} "
        + " GROUP BY d"
        + "</script>")
    List<Map<String, Object>> sumEventByDay(@Param("table") String table,
                                            @Param("dateColumn") String dateColumn,
                                            @Param("valueColumn") String valueColumn,
                                            @Param("tenantId") String tenantId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /** status_record 按日 COUNT（指定 event_type，如 DIE / ELIMINATE）。change_time 区间右开。 */
    @Select("SELECT DATE_FORMAT(change_time, '%m-%d') AS d, COUNT(*) AS v "
        + " FROM t_farm_status_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND event_type = #{eventType} "
        + "   AND change_time >= #{from} "
        + "   AND change_time <  #{to} "
        + " GROUP BY d")
    List<Map<String, Object>> countStatusEventByDay(@Param("tenantId") String tenantId,
                                                    @Param("eventType") String eventType,
                                                    @Param("from") java.time.LocalDateTime from,
                                                    @Param("to") java.time.LocalDateTime to);

    // ============================================================
    //  日情况概览 16 格 / 当日快照（FIX-MGMT-MP-BRD-001）
    //  各项取某自然日 [from, to) 右开区间内的单日值。复用上面 byDay 系列不便（单日只需标量）。
    // ============================================================

    /** 业务事件表当日 COUNT(*)（如分娩/配种/断奶/查情/打标头数）。日期列区间右开。 */
    @Select("<script>"
        + "SELECT COUNT(*) FROM ${table} "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND ${dateColumn} &gt;= #{from} "
        + "   AND ${dateColumn} &lt;  #{to} "
        + "</script>")
    int countEventInDay(@Param("table") String table,
                        @Param("dateColumn") String dateColumn,
                        @Param("tenantId") String tenantId,
                        @Param("from") java.time.LocalDateTime from,
                        @Param("to") java.time.LocalDateTime to);

    /** 业务事件表当日 SUM(valueColumn)（如产仔数/活仔数/引种头数/断奶头数）。日期列区间右开。 */
    @Select("<script>"
        + "SELECT COALESCE(SUM(${valueColumn}),0) FROM ${table} "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND ${dateColumn} &gt;= #{from} "
        + "   AND ${dateColumn} &lt;  #{to} "
        + "</script>")
    int sumEventInDay(@Param("table") String table,
                      @Param("dateColumn") String dateColumn,
                      @Param("valueColumn") String valueColumn,
                      @Param("tenantId") String tenantId,
                      @Param("from") java.time.LocalDateTime from,
                      @Param("to") java.time.LocalDateTime to);

    /** status_record 当日指定 event_type COUNT（DIE/ELIMINATE/CASTRATE）。change_time 区间右开。 */
    @Select("SELECT COUNT(*) FROM t_farm_status_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND event_type = #{eventType} "
        + "   AND change_time >= #{from} "
        + "   AND change_time <  #{to}")
    int countStatusEventInDay(@Param("tenantId") String tenantId,
                              @Param("eventType") String eventType,
                              @Param("from") java.time.LocalDateTime from,
                              @Param("to") java.time.LocalDateTime to);

    /**
     * 用药猪只数（当日）= COUNT(DISTINCT pig_id)（#7.7 第 16 格）。
     * 底表 t_breed_medicine_record（BRD-MED-003）；按 use_date 区间右开，排除 pig_id NULL（批量 master 行）。
     */
    @Select("SELECT COUNT(DISTINCT pig_id) FROM t_breed_medicine_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND pig_id IS NOT NULL "
        + "   AND use_date >= #{from} "
        + "   AND use_date <  #{to}")
    int countMedicatedPigInDay(@Param("tenantId") String tenantId,
                               @Param("from") java.time.LocalDateTime from,
                               @Param("to") java.time.LocalDateTime to);

    /**
     * 用药猪只数按日 COUNT(DISTINCT pig_id)（活动统计表第 15 行）。use_date 区间右开，排除 pig_id NULL。
     */
    @Select("SELECT DATE_FORMAT(use_date, '%m-%d') AS d, COUNT(DISTINCT pig_id) AS v "
        + " FROM t_breed_medicine_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND pig_id IS NOT NULL "
        + "   AND use_date >= #{from} "
        + "   AND use_date <  #{to} "
        + " GROUP BY d")
    List<Map<String, Object>> countMedicatedPigByDay(@Param("tenantId") String tenantId,
                                                     @Param("from") java.time.LocalDateTime from,
                                                     @Param("to") java.time.LocalDateTime to);

    // ============================================================
    //  年度繁殖与配种 + 产房仔猪质量（FIX-MGMT-MP-BRD-001，#7.1-7.5）
    //  [from, to) 右开区间内底表实时聚合。
    // ============================================================

    /** 区间内配种次数 COUNT（t_farm_pig_breeding）。 */
    @Select("SELECT COUNT(*) FROM t_farm_pig_breeding "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND breeding_date >= #{from} "
        + "   AND breeding_date <  #{to}")
    int countBreedingInRange(@Param("tenantId") String tenantId,
                             @Param("from") java.time.LocalDateTime from,
                             @Param("to") java.time.LocalDateTime to);

    /** 区间内分娩窝数 COUNT（t_farm_pig_farrow，一行一窝）。 */
    @Select("SELECT COUNT(*) FROM t_farm_pig_farrow "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND farrow_date >= #{from} "
        + "   AND farrow_date <  #{to}")
    int countFarrowLitterInRange(@Param("tenantId") String tenantId,
                                 @Param("from") java.time.LocalDateTime from,
                                 @Param("to") java.time.LocalDateTime to);

    /** 区间内总产仔数 SUM(total_born)（t_farm_pig_farrow）。 */
    @Select("SELECT COALESCE(SUM(total_born),0) FROM t_farm_pig_farrow "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND farrow_date >= #{from} "
        + "   AND farrow_date <  #{to}")
    int sumTotalBornInRange(@Param("tenantId") String tenantId,
                            @Param("from") java.time.LocalDateTime from,
                            @Param("to") java.time.LocalDateTime to);

    /** 区间内分娩活仔总数 SUM(live_born)（t_farm_pig_farrow，DATETIME 版区间右开）。 */
    @Select("SELECT COALESCE(SUM(live_born),0) FROM t_farm_pig_farrow "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND farrow_date >= #{from} "
        + "   AND farrow_date <  #{to}")
    int sumLiveBornInDateTimeRange(@Param("tenantId") String tenantId,
                                   @Param("from") java.time.LocalDateTime from,
                                   @Param("to") java.time.LocalDateTime to);

    /** 区间内断奶窝数 COUNT（t_farm_pig_weaning，一行一窝）。 */
    @Select("SELECT COUNT(*) FROM t_farm_pig_weaning "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND weaning_date >= #{from} "
        + "   AND weaning_date <  #{to}")
    int countWeaningLitterInRange(@Param("tenantId") String tenantId,
                                  @Param("from") java.time.LocalDateTime from,
                                  @Param("to") java.time.LocalDateTime to);

    /** 区间内断奶头数 SUM(weaned_count)（t_farm_pig_weaning，DATETIME 版）。 */
    @Select("SELECT COALESCE(SUM(weaned_count),0) FROM t_farm_pig_weaning "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND weaning_date >= #{from} "
        + "   AND weaning_date <  #{to}")
    int sumWeanedInDateTimeRange(@Param("tenantId") String tenantId,
                                 @Param("from") java.time.LocalDateTime from,
                                 @Param("to") java.time.LocalDateTime to);

    /** 区间内返空流头数 COUNT（t_farm_pig_abnormal，abnormal_date 区间右开）。 */
    @Select("SELECT COUNT(*) FROM t_farm_pig_abnormal "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND abnormal_date >= #{from} "
        + "   AND abnormal_date <  #{to}")
    int countAbnormalInRange(@Param("tenantId") String tenantId,
                             @Param("from") java.time.LocalDateTime from,
                             @Param("to") java.time.LocalDateTime to);

    /**
     * 断配间隔 AVG（天）：对区间内每头母猪，取其"配种日 − 该母猪上一次断奶日"的天数差，再平均。
     *
     * <p>近似实现（V1，数据量小）：以 breeding 为锚，关联同 pig_id 在配种前最近一次 weaning，
     * AVG(DATEDIFF(breeding_date, last_weaning_date))。无前序断奶的配种行（首胎/后备）不计入。</p>
     */
    @Select("SELECT AVG(DATEDIFF(b.breeding_date, w.weaning_date)) "
        + " FROM t_farm_pig_breeding b "
        + " JOIN ( "
        + "   SELECT w1.pig_id, w1.weaning_date "
        + "     FROM t_farm_pig_weaning w1 "
        + "    WHERE w1.tenant_id = #{tenantId} AND w1.del_flag = '0' "
        + " ) w ON w.pig_id = b.pig_id AND w.weaning_date <= b.breeding_date "
        + " WHERE b.tenant_id = #{tenantId} "
        + "   AND b.del_flag = '0' "
        + "   AND b.breeding_date >= #{from} "
        + "   AND b.breeding_date <  #{to} "
        + "   AND w.weaning_date = ( "
        + "     SELECT MAX(w2.weaning_date) FROM t_farm_pig_weaning w2 "
        + "      WHERE w2.tenant_id = #{tenantId} AND w2.del_flag = '0' "
        + "        AND w2.pig_id = b.pig_id AND w2.weaning_date <= b.breeding_date )")
    BigDecimal avgWeanMateIntervalDays(@Param("tenantId") String tenantId,
                                       @Param("from") java.time.LocalDateTime from,
                                       @Param("to") java.time.LocalDateTime to);

    // ============================================================
    //  育肥猪日龄分布 / 实时库存（FIX-MGMT-MP-BRD-001，#7.6）
    //  按 DATEDIFF(CURDATE(), birth_date) 落桶；END 状态排除。
    // ============================================================

    /**
     * 育肥猪日龄分布：返回 [{age:123}, ...] 每头一行的当前日龄（service 端落 6 桶，避免 SQL 写死边界）。
     * 仅 pig_type='fattening' 且未终止（current_status &lt;&gt; 'END'）且 birth_date 非空。
     */
    @Select("SELECT DATEDIFF(CURDATE(), birth_date) AS age "
        + " FROM t_farm_pig_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND pig_type = 'fattening' "
        + "   AND current_status <> 'END' "
        + "   AND birth_date IS NOT NULL")
    List<Map<String, Object>> selectFatteningAges(@Param("tenantId") String tenantId);

    /** 育肥存栏头数（pig_type='fattening' 且未终止）。 */
    @Select("SELECT COUNT(*) FROM t_farm_pig_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND pig_type = 'fattening' "
        + "   AND current_status <> 'END'")
    int countFatteningOnHand(@Param("tenantId") String tenantId);

    /**
     * 育肥猪按日龄阈值过滤 COUNT（lower 含 / upper 不含；任一为负表示无界）。
     * 用于"保育存栏(<43)"、"可出栏(>=211)"等切片。
     */
    @Select("<script>"
        + "SELECT COUNT(*) FROM t_farm_pig_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND pig_type = 'fattening' "
        + "   AND current_status &lt;&gt; 'END' "
        + "   AND birth_date IS NOT NULL "
        + "   <if test='lower >= 0'> AND DATEDIFF(CURDATE(), birth_date) &gt;= #{lower} </if> "
        + "   <if test='upper >= 0'> AND DATEDIFF(CURDATE(), birth_date) &lt;  #{upper} </if> "
        + "</script>")
    int countFatteningByAge(@Param("tenantId") String tenantId,
                            @Param("lower") int lower,
                            @Param("upper") int upper);

    // ============================================================
    //  育肥指标趋势（FIX-MGMT-MP-BRD-001）
    //  出栏按周/月分组聚合 t_farm_pig_marketing。
    // ============================================================

    /** 出栏头数按周分组（label = 周一日期 yyyy-MM-dd）。区间右开。 */
    @Select("SELECT DATE_FORMAT(DATE_SUB(marketing_date, INTERVAL WEEKDAY(marketing_date) DAY), '%Y-%m-%d') AS d, "
        + "       COUNT(*) AS v "
        + " FROM t_farm_pig_marketing "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND marketing_date >= #{from} "
        + "   AND marketing_date <  #{to} "
        + " GROUP BY d ORDER BY d")
    List<Map<String, Object>> countMarketingByWeek(@Param("tenantId") String tenantId,
                                                   @Param("from") java.time.LocalDateTime from,
                                                   @Param("to") java.time.LocalDateTime to);

    /** 出栏头数按月分组（label = yyyy-MM）。区间右开。 */
    @Select("SELECT DATE_FORMAT(marketing_date, '%Y-%m') AS d, COUNT(*) AS v "
        + " FROM t_farm_pig_marketing "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND marketing_date >= #{from} "
        + "   AND marketing_date <  #{to} "
        + " GROUP BY d ORDER BY d")
    List<Map<String, Object>> countMarketingByMonth(@Param("tenantId") String tenantId,
                                                    @Param("from") java.time.LocalDateTime from,
                                                    @Param("to") java.time.LocalDateTime to);

    // ============================================================
    //  日表落盘 row10 缺的指标（BRD-STAT-001）
    //  全部区间右开 [from, to)；DATE 列传 LocalDate，DATETIME 列传 LocalDateTime。
    // ============================================================

    /** 业务事件表 DATE 区间内 SUM(valueColumn)（如当日总产仔 total_born）。区间右开。 */
    @Select("<script>"
        + "SELECT COALESCE(SUM(${valueColumn}),0) FROM ${table} "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND ${dateColumn} &gt;= #{from} "
        + "   AND ${dateColumn} &lt;  #{to} "
        + "</script>")
    int sumEventInDayDate(@Param("table") String table,
                          @Param("dateColumn") String dateColumn,
                          @Param("valueColumn") String valueColumn,
                          @Param("tenantId") String tenantId,
                          @Param("from") java.time.LocalDate from,
                          @Param("to") java.time.LocalDate to);

    /**
     * 业务事件表区间内 COUNT(DISTINCT 列)（如分娩/配种/断奶的母猪数，去重一头多记录）。
     * dateColumn / table / distinctColumn 全部白名单内部传入。
     */
    @Select("<script>"
        + "SELECT COUNT(DISTINCT ${distinctColumn}) FROM ${table} "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND ${dateColumn} &gt;= #{from} "
        + "   AND ${dateColumn} &lt;  #{to} "
        + "</script>")
    int countDistinctEventInDay(@Param("table") String table,
                                @Param("dateColumn") String dateColumn,
                                @Param("distinctColumn") String distinctColumn,
                                @Param("tenantId") String tenantId,
                                @Param("from") java.time.LocalDate from,
                                @Param("to") java.time.LocalDate to);

    /**
     * 查情不配种数（当日）：当日有 heat 记录、且该母猪在 [heat_date, heat_date+3天] 内无 breeding 记录的母猪数。
     * 以 heat 为锚，去重母猪；NOT EXISTS 关联 3 日窗口内的 breeding。
     */
    @Select("SELECT COUNT(DISTINCT h.pig_id) "
        + " FROM t_farm_pig_heat h "
        + " WHERE h.tenant_id = #{tenantId} "
        + "   AND h.del_flag = '0' "
        + "   AND h.heat_date >= #{from} "
        + "   AND h.heat_date <  #{to} "
        + "   AND NOT EXISTS ( "
        + "     SELECT 1 FROM t_farm_pig_breeding b "
        + "      WHERE b.tenant_id = #{tenantId} AND b.del_flag = '0' "
        + "        AND b.pig_id = h.pig_id "
        + "        AND b.breeding_date >= h.heat_date "
        + "        AND b.breeding_date <  DATE_ADD(h.heat_date, INTERVAL 3 DAY) )")
    int countHeatNoBreedInDay(@Param("tenantId") String tenantId,
                              @Param("from") java.time.LocalDate from,
                              @Param("to") java.time.LocalDate to);

    /**
     * 按猪只类型统计当日 DIE 死亡头数（JOIN pig_info 取 pig_type）。
     * 用于「死亡肥猪/死亡种母猪/死亡仔猪」三项（pigType = fattening/sow/piglet）。
     * change_time 区间右开。
     */
    @Select("SELECT COUNT(*) "
        + " FROM t_farm_status_record s "
        + " JOIN t_farm_pig_info p ON p.id = s.pig_id AND p.tenant_id = s.tenant_id "
        + " WHERE s.tenant_id = #{tenantId} "
        + "   AND s.event_type = 'DIE' "
        + "   AND p.pig_type = #{pigType} "
        + "   AND s.change_time >= #{from} "
        + "   AND s.change_time <  #{to}")
    int countDeathByPigTypeInDay(@Param("tenantId") String tenantId,
                                 @Param("pigType") String pigType,
                                 @Param("from") java.time.LocalDateTime from,
                                 @Param("to") java.time.LocalDateTime to);

    /**
     * 当日出栏聚合：头数 + 出栏总重 + 平均背膘厚（取该出栏猪最新一条背膘的平均）。
     * 出栏总重 = SUM(out_weight)；背膘 = 每头出栏猪用相关标量子查询取其最新 backfat_thickness（非空），
     * 再对非空值 SUM/COUNT。标量子查询每行一值，无 JOIN 行放大；兼容 MySQL 5.7（不用 LATERAL）。
     *
     * @return {cnt:Long, weight:BigDecimal, backfatSum:BigDecimal, backfatCnt:Long}
     */
    @Select("SELECT COUNT(*) AS cnt, COALESCE(SUM(m.out_weight),0) AS weight, "
        + "       COALESCE(SUM( ( "
        + "         SELECT g.backfat_thickness FROM t_farm_pig_growth g "
        + "          WHERE g.tenant_id = m.tenant_id AND g.del_flag = '0' "
        + "            AND g.pig_id = m.pig_id AND g.backfat_thickness IS NOT NULL "
        + "          ORDER BY g.measure_date DESC LIMIT 1 ) ),0) AS backfatSum, "
        + "       SUM( CASE WHEN ( "
        + "         SELECT g.backfat_thickness FROM t_farm_pig_growth g "
        + "          WHERE g.tenant_id = m.tenant_id AND g.del_flag = '0' "
        + "            AND g.pig_id = m.pig_id AND g.backfat_thickness IS NOT NULL "
        + "          ORDER BY g.measure_date DESC LIMIT 1 ) IS NOT NULL THEN 1 ELSE 0 END ) AS backfatCnt "
        + " FROM t_farm_pig_marketing m "
        + " WHERE m.tenant_id = #{tenantId} "
        + "   AND m.del_flag = '0' "
        + "   AND m.marketing_date >= #{from} "
        + "   AND m.marketing_date <  #{to}")
    Map<String, Object> aggregateMarketingForDay(@Param("tenantId") String tenantId,
                                                 @Param("from") java.time.LocalDateTime from,
                                                 @Param("to") java.time.LocalDateTime to);

    /**
     * 当日出栏猪只的「断奶总重 + 生长总天数」。
     * 对每头当日出栏猪：取其最近一条断奶记录（关联 t_farm_pig_weaning.pig_id）的断奶总重 weaned_weight，
     * 及 DATEDIFF(出栏日, 断奶日) 天数；无断奶记录的猪跳过（不计入，null-safe）。
     *
     * @return {weanWeightSum:BigDecimal, growthDaysSum:Long}
     */
    @Select("SELECT COALESCE(SUM(w.weaned_weight),0) AS weanWeightSum, "
        + "       COALESCE(SUM(DATEDIFF(m.marketing_date, w.weaning_date)),0) AS growthDaysSum "
        + " FROM t_farm_pig_marketing m "
        + " JOIN ( "
        + "   SELECT w1.pig_id, w1.weaned_weight, w1.weaning_date "
        + "     FROM t_farm_pig_weaning w1 "
        + "    WHERE w1.tenant_id = #{tenantId} AND w1.del_flag = '0' "
        + "      AND w1.weaning_date = ( "
        + "        SELECT MAX(w2.weaning_date) FROM t_farm_pig_weaning w2 "
        + "         WHERE w2.tenant_id = #{tenantId} AND w2.del_flag = '0' "
        + "           AND w2.pig_id = w1.pig_id ) "
        + " ) w ON w.pig_id = m.pig_id "
        + " WHERE m.tenant_id = #{tenantId} "
        + "   AND m.del_flag = '0' "
        + "   AND m.marketing_date >= #{from} "
        + "   AND m.marketing_date <  #{to}")
    Map<String, Object> aggregateMarketingWeanForDay(@Param("tenantId") String tenantId,
                                                     @Param("from") java.time.LocalDateTime from,
                                                     @Param("to") java.time.LocalDateTime to);

    /**
     * 期末存栏快照：按 pig_type + current_status 维度 COUNT（T-1 当日 current_status 当前快照）。
     * 一次查回全部分组，service 端按口径汇总各期末项。
     *
     * @return 形如 [{pigType:'sow', cs:'PZ', cnt:5}, {pigType:'fattening', cs:'', cnt:30}, ...]
     */
    @Select("SELECT pig_type AS pigType, current_status AS cs, COUNT(*) AS cnt "
        + " FROM t_farm_pig_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND current_status <> 'END' "
        + " GROUP BY pig_type, current_status")
    List<Map<String, Object>> snapshotByTypeStatus(@Param("tenantId") String tenantId);

    /**
     * 期末 230 日龄以上后备母猪头数（pig_type='sow' 且 current_status='HB' 且 日龄≥230）。
     * 日龄基准 = asOf（T-1）；birth_date 非空。
     */
    @Select("SELECT COUNT(*) FROM t_farm_pig_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND pig_type = 'sow' "
        + "   AND current_status = 'HB' "
        + "   AND birth_date IS NOT NULL "
        + "   AND DATEDIFF(#{asOf}, birth_date) >= 230")
    int countReserve230(@Param("tenantId") String tenantId,
                        @Param("asOf") java.time.LocalDate asOf);

    /**
     * 当年配种批次分娩头数（落 statDate 当年）：当日分娩记录中，分娩日−114 天（≈配种日）落在 statDate 当年的活仔头数之和。
     * 邓博 row10：分娩日−114 后日期是当年则累加（按配种批次归年）。这里统计单日 statDate 内符合的 SUM(live_born)。
     */
    @Select("SELECT COALESCE(SUM(live_born),0) FROM t_farm_pig_farrow "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND farrow_date >= #{from} "
        + "   AND farrow_date <  #{to} "
        + "   AND YEAR(DATE_SUB(farrow_date, INTERVAL 114 DAY)) = #{batchYear}")
    int sumYearBatchFarrowForDay(@Param("tenantId") String tenantId,
                                 @Param("from") java.time.LocalDate from,
                                 @Param("to") java.time.LocalDate to,
                                 @Param("batchYear") int batchYear);

    // ============================================================
    //  日表回读聚合 → 月/年（BRD-STAT-001）
    //  月/年高级指标从 t_farm_indicator_record 日表 Σ 回读，避免重复扫底表。
    //  区间右开 [from, to) 按 stat_date。
    // ============================================================

    /**
     * 区间内日表指标列 Σ 汇总（月/年高级指标的 Σ日 来源）。
     * 返回各列 SUM，缺数据补 0。service 端按 row13/row14 公式二次计算率/窝均。
     *
     * @return 各 SUM 列：sumFarrowSow / sumBreedingSow / sumWeaningSow / sumAbnormal / sumTotalBorn /
     *         sumLiveBorn / sumWeanedPiglet / sumDeathPiglet / sumMarketingCount / sumMarketingWeight /
     *         sumEndProductionSow / sumEndReserve230 / sumEndNonprodSow / sumYearBatchFarrow / sumGrowthDays
     */
    @Select("SELECT "
        + "  COALESCE(SUM(farrow_sow_count),0)         AS sumFarrowSow, "
        + "  COALESCE(SUM(breeding_sow_count),0)       AS sumBreedingSow, "
        + "  COALESCE(SUM(weaning_sow_count),0)        AS sumWeaningSow, "
        + "  COALESCE(SUM(abnormal_sow_count),0)       AS sumAbnormal, "
        + "  COALESCE(SUM(total_born_count),0)         AS sumTotalBorn, "
        + "  COALESCE(SUM(live_born_count),0)          AS sumLiveBorn, "
        + "  COALESCE(SUM(weaned_piglet_count),0)      AS sumWeanedPiglet, "
        + "  COALESCE(SUM(death_piglet_count),0)       AS sumDeathPiglet, "
        + "  COALESCE(SUM(marketing_pig_count),0)      AS sumMarketingCount, "
        + "  COALESCE(SUM(marketing_weight),0)         AS sumMarketingWeight, "
        + "  COALESCE(SUM(end_production_sow_count),0) AS sumEndProductionSow, "
        + "  COALESCE(SUM(end_reserve_230_count),0)    AS sumEndReserve230, "
        + "  COALESCE(SUM(end_nonprod_sow_count),0)    AS sumEndNonprodSow, "
        + "  COALESCE(SUM(year_batch_farrow_count),0)  AS sumYearBatchFarrow, "
        + "  COALESCE(SUM(growth_total_days),0)        AS sumGrowthDays "
        + " FROM t_farm_indicator_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND stat_date >= #{from} "
        + "   AND stat_date <  #{to}")
    Map<String, Object> sumIndicatorRange(@Param("tenantId") String tenantId,
                                          @Param("from") java.time.LocalDate from,
                                          @Param("to") java.time.LocalDate to);

    /**
     * 累计匹配配种窝数（row13）：「每日用当天−114 在对应日期的配种母猪数累加」
     * 等价于 [from−114, to−114) 偏移区间的配种母猪数 COUNT(DISTINCT pig_id)。
     * from/to 传当月自然边界，方法内偏移 114 天。
     */
    @Select("SELECT COUNT(DISTINCT pig_id) FROM t_farm_pig_breeding "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND breeding_date >= DATE_SUB(#{from}, INTERVAL 114 DAY) "
        + "   AND breeding_date <  DATE_SUB(#{to}, INTERVAL 114 DAY)")
    int countMateLitterShifted(@Param("tenantId") String tenantId,
                               @Param("from") java.time.LocalDateTime from,
                               @Param("to") java.time.LocalDateTime to);

    /** 区间内日表行数（= 已落盘天数，年均能繁存栏的「已历天数」分母）。 */
    @Select("SELECT COUNT(*) FROM t_farm_indicator_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND stat_date >= #{from} "
        + "   AND stat_date <  #{to}")
    int countIndicatorDays(@Param("tenantId") String tenantId,
                           @Param("from") java.time.LocalDate from,
                           @Param("to") java.time.LocalDate to);

    /**
     * 区间内「断配间隔」总天数 + 总记录数（断奶→配种配对，DATETIME 区间右开按配种日）。
     * 复用 avgWeanMateIntervalDays 同套配对逻辑，但返回 SUM + COUNT 供月/年累计。
     *
     * @return {totalDays:Long, totalCount:Long}
     */
    @Select("SELECT COALESCE(SUM(DATEDIFF(b.breeding_date, w.weaning_date)),0) AS totalDays, "
        + "       COUNT(*) AS totalCount "
        + " FROM t_farm_pig_breeding b "
        + " JOIN ( "
        + "   SELECT w1.pig_id, w1.weaning_date "
        + "     FROM t_farm_pig_weaning w1 "
        + "    WHERE w1.tenant_id = #{tenantId} AND w1.del_flag = '0' "
        + " ) w ON w.pig_id = b.pig_id AND w.weaning_date <= b.breeding_date "
        + " WHERE b.tenant_id = #{tenantId} "
        + "   AND b.del_flag = '0' "
        + "   AND b.breeding_date >= #{from} "
        + "   AND b.breeding_date <  #{to} "
        + "   AND w.weaning_date = ( "
        + "     SELECT MAX(w2.weaning_date) FROM t_farm_pig_weaning w2 "
        + "      WHERE w2.tenant_id = #{tenantId} AND w2.del_flag = '0' "
        + "        AND w2.pig_id = b.pig_id AND w2.weaning_date <= b.breeding_date )")
    Map<String, Object> sumWeanMateIntervalRange(@Param("tenantId") String tenantId,
                                                 @Param("from") java.time.LocalDateTime from,
                                                 @Param("to") java.time.LocalDateTime to);

    // ============================================================
    //  母猪性能 per-pig 聚合（BRD-STAT-001，upsertSowPerformance 用）
    // ============================================================

    /**
     * 全部活母猪（pig_type='sow' 且 current_status&lt;&gt;'END'）的 id + ear_no + parity，
     * 供按 pig_id 逐头聚合 sow_performance。
     */
    @Select("SELECT id, ear_no AS earNo, parity "
        + " FROM t_farm_pig_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND pig_type = 'sow' "
        + "   AND current_status <> 'END'")
    List<Map<String, Object>> selectAliveSows(@Param("tenantId") String tenantId);

    /**
     * 单头母猪分娩累计：总产仔 / 总活仔 / 分娩窝数 / Σ平均出生重 / Σ平均怀孕天数（分娩−配种）。
     *
     * @return {totalBorn, totalLiveBorn, litterCount, sumAvgBornWeight, gestSum, gestCount}
     */
    @Select("SELECT COALESCE(SUM(f.total_born),0) AS totalBorn, "
        + "       COALESCE(SUM(f.live_born),0) AS totalLiveBorn, "
        + "       COUNT(*) AS litterCount, "
        + "       COALESCE(SUM(f.avg_weight),0) AS sumAvgBornWeight, "
        + "       COALESCE(SUM(CASE WHEN bd.breeding_date IS NOT NULL "
        + "                          AND DATEDIFF(f.farrow_date, bd.breeding_date) BETWEEN 0 AND 200 "
        + "                         THEN DATEDIFF(f.farrow_date, bd.breeding_date) END),0) AS gestSum, "
        + "       SUM(CASE WHEN bd.breeding_date IS NOT NULL "
        + "                 AND DATEDIFF(f.farrow_date, bd.breeding_date) BETWEEN 0 AND 200 "
        + "                THEN 1 ELSE 0 END) AS gestCount "
        + " FROM t_farm_pig_farrow f "
        + " LEFT JOIN t_farm_pig_breeding bd "
        + "        ON bd.id = f.breeding_id AND bd.tenant_id = f.tenant_id AND bd.del_flag = '0' "
        + " WHERE f.tenant_id = #{tenantId} "
        + "   AND f.del_flag = '0' "
        + "   AND f.pig_id = #{pigId}")
    Map<String, Object> sowFarrowAgg(@Param("tenantId") String tenantId,
                                     @Param("pigId") Long pigId);

    /**
     * 单头母猪断奶累计：总断奶头数 / 断奶批数 / Σ平均断奶重。
     *
     * @return {totalWeaned, weanCount, sumAvgWeanedWeight}
     */
    @Select("SELECT COALESCE(SUM(weaned_count),0) AS totalWeaned, "
        + "       COUNT(*) AS weanCount, "
        + "       COALESCE(SUM(avg_weaned_weight),0) AS sumAvgWeanedWeight "
        + " FROM t_farm_pig_weaning "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND pig_id = #{pigId}")
    Map<String, Object> sowWeanAgg(@Param("tenantId") String tenantId,
                                   @Param("pigId") Long pigId);

    /** 单头母猪返空流总次数（t_farm_pig_abnormal）。 */
    @Select("SELECT COUNT(*) FROM t_farm_pig_abnormal "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND pig_id = #{pigId}")
    int sowAbnormalCount(@Param("tenantId") String tenantId,
                         @Param("pigId") Long pigId);

    /**
     * 单头母猪「断奶→配种」配对天数（NPD 的分娩至断奶用，及断配天数）。
     * 返回 Σ(配种日−上次断奶日) + 配对数。
     *
     * @return {sumDays:Long, cnt:Long}
     */
    @Select("SELECT COALESCE(SUM(DATEDIFF(b.breeding_date, w.weaning_date)),0) AS sumDays, "
        + "       COUNT(*) AS cnt "
        + " FROM t_farm_pig_breeding b "
        + " JOIN ( "
        + "   SELECT w1.pig_id, w1.weaning_date "
        + "     FROM t_farm_pig_weaning w1 "
        + "    WHERE w1.tenant_id = #{tenantId} AND w1.del_flag = '0' AND w1.pig_id = #{pigId} "
        + " ) w ON w.pig_id = b.pig_id AND w.weaning_date <= b.breeding_date "
        + " WHERE b.tenant_id = #{tenantId} AND b.del_flag = '0' AND b.pig_id = #{pigId} "
        + "   AND w.weaning_date = ( "
        + "     SELECT MAX(w2.weaning_date) FROM t_farm_pig_weaning w2 "
        + "      WHERE w2.tenant_id = #{tenantId} AND w2.del_flag = '0' "
        + "        AND w2.pig_id = #{pigId} AND w2.weaning_date <= b.breeding_date )")
    Map<String, Object> sowWeanBreedAgg(@Param("tenantId") String tenantId,
                                        @Param("pigId") Long pigId);
}
