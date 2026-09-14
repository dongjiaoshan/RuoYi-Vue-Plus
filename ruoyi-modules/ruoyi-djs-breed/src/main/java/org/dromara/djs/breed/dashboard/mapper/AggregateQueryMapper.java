package org.dromara.djs.breed.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.breed.dashboard.domain.FarmIndicatorRecord;

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
     * 实时库存：按 pig_type 分组 COUNT（排除 lifecycle='END'），并追加一行
     * {@code pigType='reserve'} = 后备存栏（{@code current_status='HB'} 计数，与
     * {@code InventoryAppletServiceImpl} 后备段口径一致；后备猪 pig_type 仍是 sow，
     * 已含在 sow 计数内，此行为 dashboard 单列后备存栏提供计数，不改 sow 口径）。
     *
     * @param tenantId 租户
     * @return 形如 [{pig_type:'sow', cnt:23}, ..., {pig_type:'reserve', cnt:2}]
     */
    @Select("SELECT pig_type AS pigType, COUNT(*) AS cnt "
        + " FROM t_farm_pig_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND current_status <> 'END' "
        + " GROUP BY pig_type "
        + " UNION ALL "
        + " SELECT 'reserve' AS pigType, COUNT(*) AS cnt "
        + " FROM t_farm_pig_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND current_status = 'HB'")
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
     * 区间内「引种母猪数」SUM(pig_count)（row10 日表 introduce_sow_count，B4）。
     *
     * <p>口径（spec B4）：内部引种全算（视为母猪群补充）+ 外部引种仅性别为母（pig_sex='F'）。
     * 外部混批 {@code pig_sex} 为 NULL（统一时才填）→ 不计入（无法判定母数）。</p>
     */
    @Select("SELECT COALESCE(SUM(pig_count),0) FROM t_farm_pig_introduce "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND introduce_date >= #{from} "
        + "   AND introduce_date <  #{to} "
        + "   AND ( introduce_type = 'internal' "
        + "         OR (introduce_type = 'external' AND pig_sex = 'F') )")
    int sumIntroducedSowInRange(@Param("tenantId") String tenantId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    /**
     * 区间内「引种公猪数」SUM(pig_count)（row14 日表 introduce_boar_count）。
     *
     * <p>口径（客户原文）：当日外部引种的公猪头数 = {@code introduce_type='external' AND pig_sex='M'}。
     * 与母猪口径对称：内部引种全归母猪群补充（不计公猪）；外部混批 {@code pig_sex} 为 NULL 不计入。</p>
     */
    @Select("SELECT COALESCE(SUM(pig_count),0) FROM t_farm_pig_introduce "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND introduce_date >= #{from} "
        + "   AND introduce_date <  #{to} "
        + "   AND introduce_type = 'external' AND pig_sex = 'M'")
    int sumIntroducedBoarInRange(@Param("tenantId") String tenantId,
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

    /**
     * 区间内「配种母猪头数」COUNT(DISTINCT pig_id)（row13 T6 配种率分子）。
     * spec「配种母猪头数」按头去重，同母猪同区间多次配种只算一头
     * （与分娩/断奶/返空流统一去重口径），区别于 {@link #countBreedingInRange} 的配种次数。
     */
    @Select("SELECT COUNT(DISTINCT pig_id) FROM t_farm_pig_breeding "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND breeding_date >= #{from} "
        + "   AND breeding_date <  #{to}")
    int countDistinctBreedingSowInRange(@Param("tenantId") String tenantId,
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
     * 区间内「返空流母猪数」COUNT(DISTINCT pig_id)（row10 日表 abnormal_sow_count）。
     * 与分娩/配种/断奶头数统一按母猪去重口径，区别于 {@link #countAbnormalInRange} 的返空流记录数。
     */
    @Select("SELECT COUNT(DISTINCT pig_id) FROM t_farm_pig_abnormal "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND abnormal_date >= #{from} "
        + "   AND abnormal_date <  #{to}")
    int countDistinctAbnormalSowInRange(@Param("tenantId") String tenantId,
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

    /** 出栏头数按日分组（label = 日数字 "1".."31"，即 DAY(marketing_date)）。区间右开，供单月逐日趋势用。 */
    @Select("SELECT DAY(marketing_date) AS d, COUNT(*) AS v "
        + " FROM t_farm_pig_marketing "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND marketing_date >= #{from} "
        + "   AND marketing_date <  #{to} "
        + " GROUP BY d ORDER BY d")
    List<Map<String, Object>> countMarketingByDay(@Param("tenantId") String tenantId,
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
     * 查情不配种数（当日）：当日有 heat 记录的母猪数 COUNT(DISTINCT pig_id)。
     *
     * <p>按邓博字面口径直查 heat 表，<b>不</b>关联 breeding——t_farm_pig_heat 无「配种/不配种」
     * 标记（仅 heat_result 阳性/阴性/待复查 + is_pregnant_confirmed），无法判定是否后续配种，
     * 故已去掉原 NOT EXISTS breeding 3 日窗关联。当前实质等同「查情数」。</p>
     */
    @Select("SELECT COUNT(DISTINCT pig_id) "
        + " FROM t_farm_pig_heat "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND heat_date >= #{from} "
        + "   AND heat_date <  #{to}")
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
     * <p>背膘分母口径（spec row10「平均背膘厚 = 背膘之和 / 有背膘的肥猪数」）：背膘 SUM/COUNT 只算
     * {@code pig_type='fattening'} 的出栏猪（用相关子查询取 pig_info.pig_type 判定），种猪 / 仔猪出栏
     * 不计入背膘分母。cnt / weight 仍覆盖全部出栏（口径不变）。</p>
     *
     * <p>背膘 30 天窗（row182）：相关子查询只取出栏日前 30 天内（{@code measure_date >=
     * marketing_date − 30 天}）的最近一条背膘——30 天外的陈旧背膘不代表出栏体况，不计入。
     * 分子 = 符合窗口的背膘累加；分母 = 符合窗口（有近 30 天背膘）的出栏肥猪头数。</p>
     *
     * @return {cnt:Long, weight:BigDecimal, backfatSum:BigDecimal, backfatCnt:Long}
     */
    @Select("SELECT COUNT(*) AS cnt, COALESCE(SUM(m.out_weight),0) AS weight, "
        + "       COALESCE(SUM( CASE WHEN ( "
        + "           SELECT pi.pig_type FROM t_farm_pig_info pi "
        + "            WHERE pi.id = m.pig_id AND pi.tenant_id = m.tenant_id ) = 'fattening' "
        + "         THEN ( "
        + "           SELECT g.backfat_thickness FROM t_farm_pig_growth g "
        + "            WHERE g.tenant_id = m.tenant_id AND g.del_flag = '0' "
        + "              AND g.pig_id = m.pig_id AND g.backfat_thickness IS NOT NULL "
        + "              AND g.measure_date >= DATE_SUB(m.marketing_date, INTERVAL 30 DAY) "
        + "            ORDER BY g.measure_date DESC LIMIT 1 ) END ),0) AS backfatSum, "
        + "       SUM( CASE WHEN ( "
        + "           SELECT pi.pig_type FROM t_farm_pig_info pi "
        + "            WHERE pi.id = m.pig_id AND pi.tenant_id = m.tenant_id ) = 'fattening' "
        + "          AND ( "
        + "           SELECT g.backfat_thickness FROM t_farm_pig_growth g "
        + "            WHERE g.tenant_id = m.tenant_id AND g.del_flag = '0' "
        + "              AND g.pig_id = m.pig_id AND g.backfat_thickness IS NOT NULL "
        + "              AND g.measure_date >= DATE_SUB(m.marketing_date, INTERVAL 30 DAY) "
        + "            ORDER BY g.measure_date DESC LIMIT 1 ) IS NOT NULL THEN 1 ELSE 0 END ) AS backfatCnt "
        + " FROM t_farm_pig_marketing m "
        + " WHERE m.tenant_id = #{tenantId} "
        + "   AND m.del_flag = '0' "
        + "   AND m.marketing_date >= #{from} "
        + "   AND m.marketing_date <  #{to}")
    Map<String, Object> aggregateMarketingForDay(@Param("tenantId") String tenantId,
                                                 @Param("from") java.time.LocalDateTime from,
                                                 @Param("to") java.time.LocalDateTime to);

    /**
     * 当日出栏育肥猪的「个体断奶总重 + 饲养总天数 + 生长总天数」（row112 NPD 口径重构）。
     *
     * <p>育肥猪是仔猪贴标后翻成的 pig_info 行，与母猪断奶记录是不同实体——直接读出栏育肥猪
     * 自己的 pig_info 断奶溯源快照（{@code wean_weight} 个体断奶重 / {@code wean_date} 断奶日 /
     * {@code birth_date} 出生日）：
     * <ul>
     *   <li>weanTotalWeight = Σ pi.wean_weight；marketingWeightWeaned = Σ m.out_weight（同集合）。</li>
     *   <li>feedDaysSum（饲养总天数）= Σ (DATEDIFF(出栏日, 断奶日) + 1)（含头含尾，每头 +1）——从断奶起算。</li>
     *   <li>growthDaysSum（生长总天数，row186 口径）= Σ (DATEDIFF(出栏日, 出生日) + 1)；独立展示指标。
     *       生长按整个生命周期从出生起算（区别于饲养从断奶起算）；birth_date 为空的行 DATEDIFF 返 NULL，
     *       SUM 自动跳过。日增重分母用 feedDaysSum（row189 最终确认，与净增重同起点）。</li>
     * </ul>
     * 集合口径：仅计入有断奶快照的出栏猪（wean_date 非空，与净增重同集合）。net_gain/daily_gain 公式在 service 层。</p>
     *
     * @return {weanWeightSum:BigDecimal, feedDaysSum:Long, growthDaysSum:Long, marketingWeightWeaned:BigDecimal}
     */
    @Select("SELECT COALESCE(SUM(pi.wean_weight),0) AS weanWeightSum, "
        + "       COALESCE(SUM(DATEDIFF(m.marketing_date, pi.wean_date) + 1),0) AS feedDaysSum, "
        + "       COALESCE(SUM(DATEDIFF(m.marketing_date, pi.birth_date) + 1),0) AS growthDaysSum, "
        + "       COALESCE(SUM(m.out_weight),0) AS marketingWeightWeaned "
        + " FROM t_farm_pig_marketing m "
        + " JOIN t_farm_pig_info pi "
        + "   ON pi.id = m.pig_id AND pi.tenant_id = m.tenant_id AND pi.del_flag = '0' "
        + " WHERE m.tenant_id = #{tenantId} "
        + "   AND m.del_flag = '0' "
        + "   AND pi.wean_date IS NOT NULL "
        + "   AND m.marketing_date >= #{from} "
        + "   AND m.marketing_date <  #{to}")
    Map<String, Object> aggregateMarketingWeanForDay(@Param("tenantId") String tenantId,
                                                     @Param("from") java.time.LocalDateTime from,
                                                     @Param("to") java.time.LocalDateTime to);

    // ============================================================
    //  猪只每日快照（BRD-STAT-003）：期末存栏指标的唯一数据源。
    //  t_farm_pig_snapshot 是派生表，无 MP 实体，全部走原生 SQL。
    //  口径 = 「按业务时间重放出的 D 日收盘在群态」，不是「采集那一刻的主表态」——
    //  所以补录（业务日 ≠ 录入日）不会让那一天的存栏错，重放多少次结果都一样。
    // ============================================================

    /** 某日快照行数（重放后自检用：0 行说明该日没有任何在群猪，通常是数据异常）。 */
    @Select("SELECT COUNT(*) FROM t_farm_pig_snapshot "
        + " WHERE tenant_id = #{tenantId} AND snap_date = #{snapDate}")
    int countSnapshotOnDate(@Param("tenantId") String tenantId,
                            @Param("snapDate") java.time.LocalDate snapDate);

    /** 清掉某日快照（重放前清场，与 {@link #rebuildPigSnapshotForDate} 成对调用）。 */
    @org.apache.ibatis.annotations.Delete(
        "DELETE FROM t_farm_pig_snapshot WHERE tenant_id = #{tenantId} AND snap_date = #{snapDate}")
    int deletePigSnapshotOnDate(@Param("tenantId") String tenantId,
                                @Param("snapDate") java.time.LocalDate snapDate);

    /**
     * 按业务时间重放某业务日 {@code snapDate} 收盘时的在群猪群，写入快照表。
     *
     * <h3>在群判定</h3>
     * <ul>
     *   <li>入群业务日 ≤ snapDate。场内出生（{@code mother_ear} 非空）取 {@code birth_date}，
     *       其余取 {@code introduce_date}，两者缺失时互为兜底；</li>
     *   <li>snapDate 收盘前没有终止事件（{@code t_farm_status_record.new_status='END'}，
     *       覆盖出栏 / 死亡 / 淘汰），按 {@code change_time} 业务时间判。</li>
     * </ul>
     *
     * <h3>繁殖态</h3>
     * snapDate 收盘前最后一条状态记录的 {@code new_status}；一条都没有（如仔猪、或建档晚于该业务日）→ 空串。
     *
     * <h3>猪只类型</h3>
     * 依次取：① snapDate 之后最早一条类型变更记录的 {@code old_pig_type}（TRANSFER 转育肥舍 /
     * TO_FATTEN / 内部引种留种，BRD-STAT-002 落的史）；② 场内出生且现为育肥猪、而 snapDate 早于
     * 该窝断奶业务日 → 当时还是仔猪（覆盖「断奶即翻育肥」这条不走状态机的批量 update 路径，
     * 也覆盖「断奶当天才补建档案」的晚录仔猪）；③ 主表当前值。
     */
    @org.apache.ibatis.annotations.Insert(
        "INSERT INTO t_farm_pig_snapshot "
        + " (tenant_id, snap_date, pig_id, pig_type, current_status, birth_date) "
        + "SELECT #{tenantId}, #{snapDate}, p.id, "
        + "       COALESCE( "
        + "         (SELECT c.old_pig_type FROM t_farm_status_record c "
        + "           WHERE c.tenant_id = p.tenant_id AND c.pig_id = p.id "
        + "             AND c.new_pig_type IS NOT NULL "
        + "             AND c.change_time >= DATE_ADD(#{snapDate}, INTERVAL 1 DAY) "
        + "           ORDER BY c.change_time ASC, c.id ASC LIMIT 1), "
        + "         CASE WHEN p.pig_type = 'fattening' AND p.mother_ear IS NOT NULL "
        + "                   AND w.wean_date IS NOT NULL AND #{snapDate} < w.wean_date "
        + "              THEN 'piglet' END, "
        + "         p.pig_type) AS pig_type, "
        + "       COALESCE( "
        + "         (SELECT s.new_status FROM t_farm_status_record s "
        + "           WHERE s.tenant_id = p.tenant_id AND s.pig_id = p.id "
        + "             AND s.change_time < DATE_ADD(#{snapDate}, INTERVAL 1 DAY) "
        + "           ORDER BY s.change_time DESC, s.id DESC LIMIT 1), '') AS current_status, "
        + "       p.birth_date "
        + "  FROM t_farm_pig_info p "
        + "  LEFT JOIN (SELECT pn.pig_id, MIN(DATE(wn.weaning_date)) AS wean_date "
        + "               FROM t_farm_pig_pigletno pn "
        + "               JOIN t_farm_pig_weaning wn ON wn.farrow_id = pn.farrow_id "
        + "                                        AND wn.tenant_id = pn.tenant_id "
        + "                                        AND wn.del_flag = '0' "
        + "              WHERE pn.tenant_id = #{tenantId} AND pn.del_flag = '0' "
        + "                AND pn.pig_id IS NOT NULL "
        + "              GROUP BY pn.pig_id) w ON w.pig_id = p.id "
        + " WHERE p.tenant_id = #{tenantId} "
        + "   AND p.del_flag = '0' "
        + "   AND CASE WHEN p.mother_ear IS NOT NULL THEN COALESCE(p.birth_date, p.introduce_date) "
        + "            ELSE COALESCE(p.introduce_date, p.birth_date) END <= #{snapDate} "
        + "   AND NOT EXISTS (SELECT 1 FROM t_farm_status_record e "
        + "                    WHERE e.tenant_id = p.tenant_id AND e.pig_id = p.id "
        + "                      AND e.new_status = 'END' "
        + "                      AND e.change_time < DATE_ADD(#{snapDate}, INTERVAL 1 DAY))")
    int rebuildPigSnapshotForDate(@Param("tenantId") String tenantId,
                                  @Param("snapDate") java.time.LocalDate snapDate);

    /**
     * 补录检测：最近录进来、但业务日落在重算窗口之外的状态事件（按业务日分组）。
     *
     * <p>窗口内的补录由每日滚动重算自动修正；窗口外的改不动，必须人工按日期补跑
     * {@code POST /djs/breed/dashboard/trigger-aggregate?date=...}。这个查询让它可见，
     * 否则漏计会像 8 月那样静悄悄躺着（分娩少 8 窝 81 头、返空流少 5 条都是这么来的）。</p>
     */
    @Select("SELECT DATE(change_time) AS bizDate, COUNT(*) AS cnt "
        + " FROM t_farm_status_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND create_time >= #{since} "
        + "   AND change_time < #{windowStart} "
        + " GROUP BY DATE(change_time) ORDER BY bizDate")
    List<Map<String, Object>> findLateEntriesBeforeWindow(@Param("tenantId") String tenantId,
                                                          @Param("windowStart") java.time.LocalDate windowStart,
                                                          @Param("since") java.time.LocalDateTime since);

    /** 某日快照按 pig_type + current_status 分组 COUNT（fillEndStock 绑此，替代实时主表口径）。 */
    @Select("SELECT pig_type AS pigType, current_status AS cs, COUNT(*) AS cnt "
        + " FROM t_farm_pig_snapshot "
        + " WHERE tenant_id = #{tenantId} AND snap_date = #{snapDate} "
        + " GROUP BY pig_type, current_status")
    List<Map<String, Object>> snapshotByTypeStatusOnDate(@Param("tenantId") String tenantId,
                                                         @Param("snapDate") java.time.LocalDate snapDate);

    /** 某日快照内 230 日龄以上后备母猪头数（日龄基准 = snap_date；快照 birth_date）。 */
    @Select("SELECT COUNT(*) FROM t_farm_pig_snapshot "
        + " WHERE tenant_id = #{tenantId} AND snap_date = #{snapDate} "
        + "   AND pig_type = 'sow' AND current_status = 'HB' "
        + "   AND birth_date IS NOT NULL "
        + "   AND DATEDIFF(#{snapDate}, birth_date) >= 230")
    int countReserve230OnSnapshot(@Param("tenantId") String tenantId,
                                  @Param("snapDate") java.time.LocalDate snapDate);

    /**
     * 当年配种批次「分娩头数」（落 statDate 当年）：当日分娩记录中，分娩日−114 天（≈配种日）落在
     * statDate 当年的分娩窝数 COUNT(*)。
     *
     * <p>口径修正（row14 B3）：分娩头数 = 母猪头数（窝数），一行 farrow = 一窝 = 一头母猪分娩，
     * 故用 COUNT(*) 不是 SUM(live_born)（仔猪数）。这会同时修正 year_farrow_rate（年分娩率）与 PSY
     * （二者都用 year_batch_farrow_count）。</p>
     *
     * <p>配种→分娩天数偏移读配置（row183）：{@code breedToFarrowDays} 来自配置键
     * {@code sow_breed_to_farrow_days}（缺省 114），不再写死 114。</p>
     */
    @Select("SELECT COUNT(*) FROM t_farm_pig_farrow "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND farrow_date >= #{from} "
        + "   AND farrow_date <  #{to} "
        + "   AND YEAR(DATE_SUB(farrow_date, INTERVAL #{breedToFarrowDays} DAY)) = #{batchYear}")
    int sumYearBatchFarrowForDay(@Param("tenantId") String tenantId,
                                 @Param("from") java.time.LocalDate from,
                                 @Param("to") java.time.LocalDate to,
                                 @Param("batchYear") int batchYear,
                                 @Param("breedToFarrowDays") int breedToFarrowDays);

    // ============================================================
    //  配种批次（cohort）口径（BRD-STAT-COHORT-001）
    //
    //  甲方口径：配种满 judgeDays（sow_farrow_judge_deadline_days，119）仍未分娩，该头即定性为
    //  「未分娩」并计入损失；分娩率 = 该批次按期分娩数 ÷ 该批次配种数。
    //
    //  批次归属：一条配种记录 = 一个批次单元，判定日 = breeding_date + judgeDays。
    //  统计区间 [from, to) 收的是「判定日落在区间内」的批次 —— 等价写成
    //  breeding_date ∈ [from − judgeDays, to − judgeDays)，可走 idx_breeding_date。
    //  右开界 to 由调用方传 T-1 收口值，故落在区间内的批次天然都已到期，无需再比 NOW()。
    //
    //  与旧口径的区别：分子分母是**同一批猪**。旧口径分子取当期分娩窝数、分母取偏移窗配种数，
    //  两者只是数量上近似；且分子走日表 Σ（日表 7/31 才起且不回补，实测漏 40%），
    //  cohort 直扫底表，不受日表覆盖度影响。
    // ============================================================

    /** cohort 去向归集的公共 FROM/JOIN（判定日区间 + 每批次一行结局）。 */
    String COHORT_FROM =
          "   FROM t_farm_pig_breeding b "
        + "   JOIN t_farm_pig_info p ON p.id = b.pig_id "
        + "   LEFT JOIN (SELECT f0.breeding_id AS bid, "
        + "                     MIN(DATEDIFF(f0.farrow_date, b0.breeding_date)) AS dd "
        + "                FROM t_farm_pig_farrow f0 "
        + "                JOIN t_farm_pig_breeding b0 ON b0.id = f0.breeding_id "
        + "               WHERE f0.tenant_id = #{tenantId} AND f0.del_flag = '0' "
        + "               GROUP BY f0.breeding_id) f ON f.bid = b.id "
        + "   LEFT JOIN (SELECT related_breeding_id AS bid, "
        + "                     SUBSTRING_INDEX(GROUP_CONCAT(abnormal_type "
        + "                       ORDER BY abnormal_date, id), ',', 1) AS tp "
        + "                FROM t_farm_pig_abnormal "
        + "               WHERE tenant_id = #{tenantId} AND del_flag = '0' "
        + "                 AND related_breeding_id IS NOT NULL "
        + "               GROUP BY related_breeding_id) a ON a.bid = b.id "
        + "  WHERE b.tenant_id = #{tenantId} "
        + "    AND b.del_flag = '0' ";

    /**
     * 每个批次归一个结局（互斥，SUM 后各桶之和 = 批次总数）。
     *
     * <p>优先级 分娩 &gt; 返空流 &gt; 离群 &gt; 未定性：同一条配种记录正常只有一个结局；
     * 「返情后复配再分娩」在底表是**另一条**配种记录，不会和本条抢桶。
     * {@code FARROW_LATE}（超 judgeDays 才分娩）按甲方口径在判定时已算「未分娩」，
     * 故不进分子，单列一桶便于对账追查。</p>
     */
    String COHORT_OUTCOME_CASE =
          "     CASE WHEN f.dd IS NOT NULL AND f.dd <= #{judgeDays} THEN 'FARROW' "
        + "          WHEN f.dd IS NOT NULL                          THEN 'FARROW_LATE' "
        + "          WHEN a.tp = 'R' THEN 'RETURN' "
        + "          WHEN a.tp = 'N' THEN 'EMPTY' "
        + "          WHEN a.tp = 'A' THEN 'ABORT' "
        + "          WHEN p.current_status = 'END' THEN 'GONE' "
        + "          ELSE 'UNDECIDED' END ";

    /**
     * 判定日落在 [from, to) 的配种批次去向汇总（月/年分娩率的分子分母来源）。
     *
     * @param judgeDays 分娩判定节点天数（sow_farrow_judge_deadline_days，缺省 119）
     * @return {bred, farrow, farrowLate, returnCount, emptyCount, abortCount, goneCount, undecided}
     */
    @Select("SELECT COUNT(*) AS bred, "
        + "        COALESCE(SUM(outcome = 'FARROW'),0)      AS farrow, "
        + "        COALESCE(SUM(outcome = 'FARROW_LATE'),0) AS farrowLate, "
        + "        COALESCE(SUM(outcome = 'RETURN'),0)      AS returnCount, "
        + "        COALESCE(SUM(outcome = 'EMPTY'),0)       AS emptyCount, "
        + "        COALESCE(SUM(outcome = 'ABORT'),0)       AS abortCount, "
        + "        COALESCE(SUM(outcome = 'GONE'),0)        AS goneCount, "
        + "        COALESCE(SUM(outcome = 'UNDECIDED'),0)   AS undecided "
        + "   FROM (SELECT " + COHORT_OUTCOME_CASE + " AS outcome "
        + COHORT_FROM
        + "    AND b.breeding_date >= DATE_SUB(#{from}, INTERVAL #{judgeDays} DAY) "
        + "    AND b.breeding_date <  DATE_SUB(#{to},   INTERVAL #{judgeDays} DAY)) t")
    Map<String, Object> selectCohortOutcome(@Param("tenantId") String tenantId,
                                            @Param("from") java.time.LocalDate from,
                                            @Param("to") java.time.LocalDate to,
                                            @Param("judgeDays") int judgeDays);

    /**
     * 批次去向台账：按**配种月**分组，供甲方对账「这批配了多少 → 损失在哪 → 分娩多少」。
     *
     * <p>按配种月（而非判定月）分组是因为甲方就是这么问的（「8 月份分娩的猪往前推前期一共配种多少头」）。
     * {@code pending} = 判定日还没到、结局未定，与 {@code undecided}（已到期却查不到任何记录，
     * 需要现场补录定性）分开 —— 前者是正常在途，后者是待办。</p>
     *
     * @param asOf 判定基准日（传 T-1 收口值，与月/年统计口径一致）
     */
    @Select("SELECT DATE_FORMAT(bred_date, '%Y-%m') AS breedMonth, "
        + "        COUNT(*) AS bred, "
        + "        COALESCE(SUM(matured),0)                            AS matured, "
        + "        COALESCE(SUM(matured AND outcome = 'FARROW'),0)      AS farrow, "
        + "        COALESCE(SUM(matured AND outcome = 'FARROW_LATE'),0) AS farrowLate, "
        + "        COALESCE(SUM(outcome = 'RETURN'),0)                 AS returnCount, "
        + "        COALESCE(SUM(outcome = 'EMPTY'),0)                  AS emptyCount, "
        + "        COALESCE(SUM(outcome = 'ABORT'),0)                  AS abortCount, "
        + "        COALESCE(SUM(outcome = 'GONE'),0)                   AS goneCount, "
        + "        COALESCE(SUM(matured AND outcome = 'UNDECIDED'),0)  AS undecided, "
        + "        COALESCE(SUM(NOT matured AND outcome = 'UNDECIDED'),0) AS pending, "
        + "        MIN(deadline) AS firstDeadline, MAX(deadline) AS lastDeadline "
        + "   FROM (SELECT b.breeding_date AS bred_date, "
        + "                DATE(DATE_ADD(b.breeding_date, INTERVAL #{judgeDays} DAY)) AS deadline, "
        + "                DATE_ADD(b.breeding_date, INTERVAL #{judgeDays} DAY) <= #{asOf} AS matured, "
        + COHORT_OUTCOME_CASE + " AS outcome "
        + COHORT_FROM
        + "    AND b.breeding_date >= #{from} "
        + "    AND b.breeding_date <  #{to}) t "
        + "  GROUP BY breedMonth ORDER BY breedMonth")
    List<Map<String, Object>> selectCohortLedgerByBreedMonth(@Param("tenantId") String tenantId,
                                                             @Param("from") java.time.LocalDate from,
                                                             @Param("to") java.time.LocalDate to,
                                                             @Param("judgeDays") int judgeDays,
                                                             @Param("asOf") java.time.LocalDate asOf);

    /**
     * 已到期但查不到任何结局记录的批次明细（「超期未定性」待办清单）。
     *
     * <p>甲方原话「超过 119 天必须要系统内给这个猪定性是分娩了还是没分娩」—— 这些就是要现场去定性的。
     * 不定性它们就一直挂在分娩率分母里无处归。</p>
     */
    @Select("SELECT b.id AS breedingId, b.ear_no AS earNo, DATE(b.breeding_date) AS breedingDate, "
        + "        DATE(DATE_ADD(b.breeding_date, INTERVAL #{judgeDays} DAY)) AS deadline, "
        + "        DATEDIFF(#{asOf}, DATE_ADD(b.breeding_date, INTERVAL #{judgeDays} DAY)) AS overdueDays, "
        + "        b.parity, b.barn_name AS barnName, b.pen_name AS penName, p.current_status AS currentStatus "
        + COHORT_FROM
        + "    AND DATE_ADD(b.breeding_date, INTERVAL #{judgeDays} DAY) <= #{asOf} "
        + "    AND f.dd IS NULL AND a.tp IS NULL AND p.current_status <> 'END' "
        + "  ORDER BY b.breeding_date")
    List<Map<String, Object>> selectOverdueUndecided(@Param("tenantId") String tenantId,
                                                     @Param("judgeDays") int judgeDays,
                                                     @Param("asOf") java.time.LocalDate asOf);

    /**
     * 断奶记录最早业务日（PSY 年化窗口的起点）。
     *
     * <p>PSY 分子是断奶仔猪数。断奶登记 2026-08 才开始用，若分母（母猪头日）取全年而分子只有两个月，
     * 年化会把这个残缺比值再放大 365/N 倍。故窗口锚到「断奶数据可信起始日」，分子分母同区间。</p>
     */
    @Select("SELECT DATE(MIN(weaning_date)) FROM t_farm_pig_weaning "
        + " WHERE tenant_id = #{tenantId} AND del_flag = '0' "
        + "   AND weaning_date >= #{from} AND weaning_date < #{to}")
    java.time.LocalDate selectMinWeaningDate(@Param("tenantId") String tenantId,
                                             @Param("from") java.time.LocalDate from,
                                             @Param("to") java.time.LocalDate to);

    /**
     * 产房损失：按窝取该窝哺乳期死淘数与该窝活仔数（{@code weaning.farrow_id} 关联），只统计已断奶的窝。
     *
     * <p>分子取 {@code lactation_death_count}（断奶登记时填的本窝哺乳期死淘数，D-0065），不取
     * {@code live_born − weaned_count}：贴标率不足时断奶清单的铺行数 &lt; 活仔数，差值里会混进
     * 「没贴标所以没进清单」的头，间接算法会把它误判成死亡。</p>
     *
     * <p>更早的口径分子取 {@code t_farm_status_record} 里 pig_type='piglet' 的 DIE 事件，要求仔猪有个体档案；
     * 哺乳期仔猪多数还没打耳标，分子恒 0。分母只算已断奶的窝，避免把「已分娩但还没到断奶期」的窝算成损失。</p>
     *
     * @return {liveBorn, lactationDeath}
     */
    @Select("SELECT COALESCE(SUM(f.live_born),0) AS liveBorn, "
        + "        COALESCE(SUM(w.lactation_death_count),0) AS lactationDeath "
        + "   FROM t_farm_pig_weaning w "
        + "   JOIN t_farm_pig_farrow f ON f.id = w.farrow_id AND f.del_flag = '0' "
        + "  WHERE w.tenant_id = #{tenantId} AND w.del_flag = '0' "
        + "    AND w.weaning_date >= #{from} AND w.weaning_date < #{to}")
    Map<String, Object> selectFarrowHouseLoss(@Param("tenantId") String tenantId,
                                              @Param("from") java.time.LocalDate from,
                                              @Param("to") java.time.LocalDate to);

    /**
     * 区间内断奶窝数（PSY 不再用，产房/窝均口径核对用）。
     */
    @Select("SELECT COUNT(*) FROM t_farm_pig_weaning "
        + " WHERE tenant_id = #{tenantId} AND del_flag = '0' "
        + "   AND weaning_date >= #{from} AND weaning_date < #{to}")
    int countWeaningLitterInRange(@Param("tenantId") String tenantId,
                                  @Param("from") java.time.LocalDate from,
                                  @Param("to") java.time.LocalDate to);

    /**
     * 区间内 Σ日期末生产母猪头数（= 母猪头日，PSY / NPD 年化的分母）。
     * 与 {@link #sumIndicatorRange} 的 sumEndProductionSow 同源，此处按任意区间单独取，
     * 便于 PSY 用「断奶可信窗口」而非整年。
     *
     * @return {sowDays, dayRows}
     */
    @Select("SELECT COALESCE(SUM(end_production_sow_count),0) AS sowDays, COUNT(*) AS dayRows "
        + "   FROM t_farm_indicator_record "
        + "  WHERE tenant_id = #{tenantId} AND del_flag = '0' "
        + "    AND stat_date >= #{from} AND stat_date < #{to}")
    Map<String, Object> selectSowDaysInRange(@Param("tenantId") String tenantId,
                                             @Param("from") java.time.LocalDate from,
                                             @Param("to") java.time.LocalDate to);

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
     *         sumLiveBorn / sumWeanedPiglet / sumDeathPig / sumCullingPig / sumDeathPiglet / sumDeathFattening /
     *         sumMarketingCount / sumMarketingWeight / sumEndProductionSow / sumEndReserve230 / sumEndReserve /
     *         sumEndNonprodSow / sumYearBatchFarrow / sumGrowthDays
     */
    @Select("SELECT "
        + "  COALESCE(SUM(farrow_sow_count),0)         AS sumFarrowSow, "
        + "  COALESCE(SUM(breeding_sow_count),0)       AS sumBreedingSow, "
        + "  COALESCE(SUM(weaning_sow_count),0)        AS sumWeaningSow, "
        + "  COALESCE(SUM(abnormal_sow_count),0)       AS sumAbnormal, "
        + "  COALESCE(SUM(introduce_sow_count),0)      AS sumIntroduceSow, "
        + "  COALESCE(SUM(introduce_boar_count),0)     AS sumIntroduceBoar, "
        + "  COALESCE(SUM(total_born_count),0)         AS sumTotalBorn, "
        + "  COALESCE(SUM(live_born_count),0)          AS sumLiveBorn, "
        + "  COALESCE(SUM(weaned_piglet_count),0)      AS sumWeanedPiglet, "
        + "  COALESCE(SUM(death_pig_count),0)          AS sumDeathPig, "
        + "  COALESCE(SUM(culling_pig_count),0)        AS sumCullingPig, "
        + "  COALESCE(SUM(death_piglet_count),0)       AS sumDeathPiglet, "
        + "  COALESCE(SUM(death_fattening_count),0)    AS sumDeathFattening, "
        + "  COALESCE(SUM(marketing_pig_count),0)      AS sumMarketingCount, "
        + "  COALESCE(SUM(marketing_weight),0)         AS sumMarketingWeight, "
        + "  COALESCE(SUM(end_production_sow_count),0) AS sumEndProductionSow, "
        + "  COALESCE(SUM(end_reserve_230_count),0)    AS sumEndReserve230, "
        + "  COALESCE(SUM(end_reserve_count),0)        AS sumEndReserve, "
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
     * 区间内月表基础指标 Σ 汇总（年表基础指标的「取月表汇总」来源，row44）。
     * 年表按「已有单月统计的月直接取月表汇总」而非重扫业务表——月表各行本身已按日表 Σ
     * 落盘（当月行为 T-1 口径），Σ 月即得 T-1 年度总量，与月表逐行一致。
     * stat_month 闭区间 [fromMonth, toMonth]（'yyyy-MM' 字符串按字典序，等价月份序）。
     * 缺数据补 0；返回 rowCnt=有效月行数（0 → service 回落业务表兜底）。
     *
     * @return introduceCount / introduceBoarCount / bornCount / weanedCount / deathCount / cullingCount /
     *         marketingCount / marketingWeight / rowCnt
     */
    @Select("SELECT "
        + "  COALESCE(SUM(introduce_count),0)       AS introduceCount, "
        + "  COALESCE(SUM(introduce_boar_count),0)  AS introduceBoarCount, "
        + "  COALESCE(SUM(born_count),0)       AS bornCount, "
        + "  COALESCE(SUM(weaned_count),0)     AS weanedCount, "
        + "  COALESCE(SUM(death_count),0)      AS deathCount, "
        + "  COALESCE(SUM(culling_count),0)    AS cullingCount, "
        + "  COALESCE(SUM(marketing_count),0)  AS marketingCount, "
        + "  COALESCE(SUM(marketing_weight),0) AS marketingWeight, "
        + "  COUNT(*)                          AS rowCnt "
        + " FROM t_farm_monthly_production "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND stat_month >= #{fromMonth} "
        + "   AND stat_month <= #{toMonth}")
    Map<String, Object> sumMonthlyProductionRange(@Param("tenantId") String tenantId,
                                                  @Param("fromMonth") String fromMonth,
                                                  @Param("toMonth") String toMonth);

    /**
     * 区间内日表整行明细（按 stat_date 升序），供 mp「种猪场活动统计」逐日逐指标展示。
     * 与 {@link #sumIndicatorRange} 的差别：本方法不汇总，逐日返回每行全部指标列。
     * 显式带 tenant_id + del_flag='0'；stat_date 右开区间 [from, to)。
     */
    @Select("SELECT * FROM t_farm_indicator_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND stat_date >= #{fromDate} "
        + "   AND stat_date <  #{toDate} "
        + " ORDER BY stat_date ASC")
    List<FarmIndicatorRecord> selectIndicatorRecordsInRange(@Param("tenantId") String tenantId,
                                                            @Param("fromDate") LocalDate fromDate,
                                                            @Param("toDate") LocalDate toDate);

    /**
     * 累计匹配配种窝数（row13 T4）：「每日用当天−114 在对应日期的配种母猪数累加」
     * = Σ日配种母猪数。同一母猪在偏移窗内不同日各计一次，故用 COUNT(*) 不去重
     * （去 DISTINCT 会把同母猪多日配种压成 1，与「Σ日」口径不符）。
     * 等价于 [from−N, to−N) 偏移区间的配种记录数 COUNT(*)，N = breedToFarrowDays。
     * from/to 传当月自然边界，方法内偏移 N 天。
     *
     * <p>配种→分娩天数偏移读配置（row183）：{@code breedToFarrowDays} 来自配置键
     * {@code sow_breed_to_farrow_days}（缺省 114），不再写死 114。</p>
     */
    @Select("SELECT COUNT(*) FROM t_farm_pig_breeding "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND breeding_date >= DATE_SUB(#{from}, INTERVAL #{breedToFarrowDays} DAY) "
        + "   AND breeding_date <  DATE_SUB(#{to}, INTERVAL #{breedToFarrowDays} DAY)")
    int countMateLitterShifted(@Param("tenantId") String tenantId,
                               @Param("from") java.time.LocalDateTime from,
                               @Param("to") java.time.LocalDateTime to,
                               @Param("breedToFarrowDays") int breedToFarrowDays);

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
     * 区间内「断配间隔」总天数 + 总记录数（断奶→配种配对，DATETIME 区间右开按配种日；月/年共用）。
     *
     * <p>口径修正（row13 T5）：每条区间内配种只配「紧邻它前一次断奶」——用相关子查询取该母猪
     * {@code <= 配种日} 的 MAX(weaning_date)，避免对该母猪所有历史断奶做笛卡尔积灌水（旧版 JOIN
     * 无最近一次约束，一头母猪多次断奶会重复计入）。再加 {@code DATEDIFF BETWEEN 0 AND 60} 上界
     * 守卫（断配间隔正常 < 1 个月，> 60 天视为跨胎错配/脏数据剔除）。</p>
     *
     * @return {totalDays:Long, totalCount:Long}
     */
    @Select("SELECT COALESCE(SUM(DATEDIFF(b.breeding_date, w.weaning_date)),0) AS totalDays, "
        + "       COUNT(*) AS totalCount "
        + " FROM t_farm_pig_breeding b "
        + " JOIN t_farm_pig_weaning w "
        + "   ON w.pig_id = b.pig_id AND w.tenant_id = b.tenant_id AND w.del_flag = '0' "
        + " WHERE b.tenant_id = #{tenantId} "
        + "   AND b.del_flag = '0' "
        + "   AND b.breeding_date >= #{from} "
        + "   AND b.breeding_date <  #{to} "
        + "   AND w.weaning_date = ( "
        + "     SELECT MAX(w2.weaning_date) FROM t_farm_pig_weaning w2 "
        + "      WHERE w2.tenant_id = #{tenantId} AND w2.del_flag = '0' "
        + "        AND w2.pig_id = b.pig_id AND w2.weaning_date <= b.breeding_date ) "
        + "   AND DATEDIFF(b.breeding_date, w.weaning_date) BETWEEN 0 AND 60")
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
     * 单头母猪分娩累计：总产仔 / 总活仔 / 分娩窝数 / Σ平均出生重。
     *
     * @return {totalBorn, totalLiveBorn, litterCount, sumAvgBornWeight}
     */
    @Select("SELECT COALESCE(SUM(f.total_born),0) AS totalBorn, "
        + "       COALESCE(SUM(f.live_born),0) AS totalLiveBorn, "
        + "       COUNT(*) AS litterCount, "
        + "       COALESCE(SUM(f.avg_weight),0) AS sumAvgBornWeight "
        + " FROM t_farm_pig_farrow f "
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
     * 单头母猪「平均怀孕天数」（row94 口径）：状态变更记录表 t_farm_status_record 中
     * 配种(old_status='PZ') → 分娩(new_status='FM') 的流转记录，Σ(duration_days) 与条数。
     *
     * @return {sumDays, cnt}
     */
    @Select("SELECT COALESCE(SUM(duration_days),0) AS sumDays, COUNT(*) AS cnt "
        + " FROM t_farm_status_record "
        + " WHERE tenant_id = #{tenantId} AND pig_id = #{pigId} "
        + "   AND old_status = 'PZ' AND new_status = 'FM'")
    Map<String, Object> sowGestationByStatus(@Param("tenantId") String tenantId,
                                             @Param("pigId") Long pigId);

    /**
     * 单头母猪「断奶-配种天数」（row97/183 口径）：状态变更记录表 t_farm_status_record 中
     * 断奶(old_status='DN') → 配种(new_status='PZ') 的流转记录，Σ(duration_days) 与条数。
     *
     * @return {sumDays, cnt}
     */
    @Select("SELECT COALESCE(SUM(duration_days),0) AS sumDays, COUNT(*) AS cnt "
        + " FROM t_farm_status_record "
        + " WHERE tenant_id = #{tenantId} AND pig_id = #{pigId} "
        + "   AND old_status = 'DN' AND new_status = 'PZ'")
    Map<String, Object> sowWeanBreedByStatus(@Param("tenantId") String tenantId,
                                             @Param("pigId") Long pigId);

    /**
     * 单头母猪 NPD 天数（row113 母猪性能，邓博 2026-07-05 口径 = admin 测试表 row202）。
     *
     * <p>数据源 {@code t_farm_status_record}：old_status ∈ {流产 LC / 空怀 KH / 返情 FQ / 断奶 DN}
     * 且（new_status = 配种 PZ 或 event_type ∈ {死亡 DIE / 淘汰 ELIMINATE}）的记录，Σ(duration_days)。</p>
     *
     * <p>语义：每一段「非生产状态（流产/空怀/返情/断奶）持续到再次配种 / 死淘」的天数（duration_days = 本次状态
     * 持续天数）之和 = 该母猪总非生产天数 NPD。{@code t_farm_status_record} 无 del_flag（append-only 流水）。</p>
     *
     * @return Σ duration_days（无匹配返 0）
     */
    @Select("SELECT COALESCE(SUM(duration_days),0) "
        + " FROM t_farm_status_record "
        + " WHERE tenant_id = #{tenantId} AND pig_id = #{pigId} "
        + "   AND old_status IN ('LC','KH','FQ','DN') "
        + "   AND (new_status = 'PZ' OR event_type IN ('DIE','ELIMINATE'))")
    java.math.BigDecimal sumSowNpdDurationDays(@Param("tenantId") String tenantId,
                                               @Param("pigId") Long pigId);
}
