package org.dromara.djs.breed.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
