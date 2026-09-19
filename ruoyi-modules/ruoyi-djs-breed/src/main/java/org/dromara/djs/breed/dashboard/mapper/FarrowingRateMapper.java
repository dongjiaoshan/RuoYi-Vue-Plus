package org.dromara.djs.breed.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.dashboard.domain.FarrowingRate;

import java.time.LocalDate;
import java.util.Map;

/**
 * 同期配种分娩记录表 Mapper（BRD-STAT-FARROWRATE-001，表 {@code t_farm_farrowing_rate}）。
 *
 * <p>两类方法：</p>
 * <ul>
 *   <li><b>四步刷新</b>（{@code refreshStep1..4} + {@link #softDeleteOrphans}）—— 与甲方 row229 原文
 *       同序：配种 → 分娩 → 返空流 → 死淘。</li>
 *   <li><b>分娩率取数</b>（{@link #selectFarrowRate}）—— mp 月/年分娩率与月表/年表落盘共用的同一份实现。</li>
 * </ul>
 *
 * <p><b>🔴 窗口口径只有一处定义：{@link #WIN_SRC} / {@link #WIN_LEDGER}，六条语句逐字共用。</b></p>
 *
 * <p>窗口 = {@code breeding_date ∈ [from − judgeDays, to)}，源侧圈 {@code b.}、台账侧圈 {@code r.}，
 * 表达式完全相同。这么做的唯一理由是：<b>同一个窗口口径此前被写了五遍、分布在五个不同的日期空间里，
 * 结果连错三版</b>（只圈预估日 → 新录配种等 ~3.5 个月才进表；只圈业务日 → 正在出数的那批
 * 与刷新窗交集为 0；并集但第一步漏了台账侧 → 改配种日后台账永久陈旧）。压成一个表达式之后，
 * 加第七步也不可能把窗口写错。</p>
 *
 * <p>为什么这一个区间够用：{@code expected = breeding_date + judgeDays}，所以
 * 「预估分娩日 ∈ [from,to)」⟺「配种日 ∈ [from−judge, to−judge)」，是本区间的<b>真子集</b>。
 * 单区间因此严格覆盖「新录」与「本轮成熟」两头，还顺带消掉了两段区间之间那 27 天缺口
 * （窗口跨度 ≈92 天 &lt; judgeDays 119 时会出现），并且是 {@code breeding_date} 上的纯范围条件、
 * 走得了索引 —— 旧的并集写法含 {@code DATE(x) + INTERVAL} 不可 sarg，实测退化成全表扫，
 * 在 REPEATABLE READ 下会把整张配种表 S 锁住直到聚合事务结束。</p>
 *
 * <p><b>已知且可接受的缺口</b>：业务日落在区间之外的补录（例如今天补录半年前的配种/分娩）
 * 夜跑收不到，需人工 {@code trigger-aggregate?date=&lt;业务日&gt;} 补跑 —— 窗口在业务日空间里，
 * 照着告警指引跑就能修。这是滚动窗的固有性质，非本表独有。</p>
 *
 * @author djs
 * @since BRD-STAT-FARROWRATE-001
 */
@Mapper
public interface FarrowingRateMapper extends BaseMapperPlus<FarrowingRate, FarrowingRate> {

    /**
     * {@link #selectFarrowRate} 结果集的列别名 —— <b>SQL 与 Java 侧共用同一组常量</b>。
     *
     * <p>为什么不直接写字面量：service 单测里本 mapper 是 mock，返回的 map key 由测试自己写，
     * 真 SQL 的别名与 {@code mapInt(r, "...")} 读的 key <b>从来不会在测试里碰面</b>。
     * 2026-09-18 对抗验收实测：把 {@code AS numer} 改名成 {@code AS numerCnt}，
     * 生产分子恒 0，415 个单测全绿、无任何告警。共用常量后改名会同时改掉两侧，编译期就对上。</p>
     */
    /**
     * <b>窗口口径（源侧）</b> —— 六条语句共用，改窗口只改这两个常量。
     *
     * <p>{@code breeding_date ∈ [from − judgeDays, to)}。纯范围条件，可走
     * {@code t_farm_pig_breeding} 上 breeding_date 的索引。</p>
     */
    String WIN_SRC = " AND b.breeding_date >= #{from} - INTERVAL #{judgeDays} DAY "
        + "     AND b.breeding_date <  #{to} ";

    /** <b>窗口口径（台账侧）</b> —— 与 {@link #WIN_SRC} 同一个区间，只是换成台账自己的列。 */
    String WIN_LEDGER = " AND r.breeding_date >= #{from} - INTERVAL #{judgeDays} DAY "
        + "     AND r.breeding_date <  #{to} ";

    String K_DENOM = "denom";
    /** @see #K_DENOM */
    String K_NUMER = "numer";
    /** @see #K_DENOM */
    String K_FARROW_LATE = "farrowLate";

    /**
     * 第一步：以配种记录表铺底 —— 每条配种一行，写猪只ID/耳号/配种ID/配种日/预估分娩日。
     *
     * <p>窗口圈 {@code b.breeding_date}（不加 DATE() 包裹，保持可用索引）。</p>
     *
     * <p>{@code del_flag='0'} 写进 ON DUPLICATE KEY UPDATE 是为了支持「配种记录软删后又恢复」：
     * 不翻回来的话 {@code uk_tenant_breeding} 会让它永远插不进来，分母从此少一条且没有任何信号。</p>
     *
     * <p>{@code LEFT JOIN t_farm_pig_info} 而非内连接：耳号只是冗余展示列，猪只主表缺行不该让整条
     * 配种记录从表里消失（甲方原文是「根据配种记录表写入」，没说要猪只存在）。⚠️ 这与
     * {@code AggregateQueryMapper.COHORT_FROM} 的内连接不同，孤儿配种记录会让两者差 1 —— 年度聚合里
     * 有一条 live-vs-台账 的交叉校验会把这类差异报出来。</p>
     *
     * @param judgeDays 判定节点天数（{@code sow_farrow_judge_deadline_days}，缺省 119）
     */
    @Update("INSERT INTO t_farm_farrowing_rate "
        + "  (tenant_id, pig_id, ear_tag, breeding_id, breeding_date, expected_farrow_date, "
        + "   create_by, create_time, del_flag) "
        + "SELECT b.tenant_id, b.pig_id, p.ear_tag, b.id, DATE(b.breeding_date), "
        + "       DATE(b.breeding_date) + INTERVAL #{judgeDays} DAY, 1, NOW(), '0' "
        + "  FROM t_farm_pig_breeding b "
        + "  LEFT JOIN t_farm_pig_info p ON p.id = b.pig_id AND p.del_flag = '0' "
        + " WHERE b.tenant_id = #{tenantId} AND b.del_flag = '0' "
        + WIN_SRC
        + "ON DUPLICATE KEY UPDATE "
        + "   del_flag = '0', "
        + "   update_time = IF(del_flag = '0', update_time, NOW())")
    int refreshStep1Breeding(@Param("tenantId") String tenantId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("judgeDays") int judgeDays);

    /**
     * 第 1b 步：台账侧重同步 —— 按<b>源表现值</b>重算已有行的 pig_id / 耳号 / 配种日 / 预估分娩日。
     *
     * <p><b>为什么第一步给不了这个</b>：第一步是 {@code INSERT ... SELECT}，它的 WHERE 只能看源表
     * {@code b.*}。源配种记录的<b>日期被改掉</b>之后（工人录错日期后订正），台账里那行的
     * {@code breeding_date} / {@code expected_farrow_date} 仍是旧值，而第一步的 ODKU 只有在
     * 新日期仍落在窗口内时才会碰到它 —— 往过去改就永远碰不到。</p>
     *
     * <p>实测（2026-09-19 对抗验收）：把一条配种日从 03-20 改到 01-10 后连跑 <b>200 个夜跑</b>，
     * 台账一个字没变，而且三道守卫全瞎 —— {@code countJudgeDaysDrift} 比的是台账自己的
     * {@code breeding_date + judgeDays} 与 {@code expected_farrow_date}（两边一起陈旧、自洽）；
     * 年度 live-vs-台账交叉校验因为不跨年、年总数不变而无感；Σ月表对账两边同源。
     * 结果是月度分布永久错且零信号。</p>
     *
     * <p>本步按<b>台账自己的</b> {@code breeding_date} 圈窗（{@link #WIN_LEDGER}），所以陈旧行
     * 只要旧值还在窗口内就能被捞回来重算。带 {@code NOT (... <=> ...)} 闸，值没变时不写。</p>
     */
    @Update("UPDATE t_farm_farrowing_rate r "
        + "  JOIN t_farm_pig_breeding b "
        + "    ON b.id = r.breeding_id AND b.tenant_id = r.tenant_id AND b.del_flag = '0' "
        + "  LEFT JOIN t_farm_pig_info p ON p.id = b.pig_id AND p.del_flag = '0' "
        + "   SET r.pig_id = b.pig_id, "
        + "       r.ear_tag = p.ear_tag, "
        + "       r.breeding_date = DATE(b.breeding_date), "
        + "       r.expected_farrow_date = DATE(b.breeding_date) + INTERVAL #{judgeDays} DAY, "
        + "       r.update_time = NOW() "
        + " WHERE r.tenant_id = #{tenantId} AND r.del_flag = '0' "
        + WIN_LEDGER
        + "   AND NOT (r.pig_id <=> b.pig_id "
        + "        AND r.ear_tag <=> p.ear_tag "
        + "        AND r.breeding_date <=> DATE(b.breeding_date) "
        + "        AND r.expected_farrow_date <=> DATE(b.breeding_date) + INTERVAL #{judgeDays} DAY)")
    int refreshStep1bResync(@Param("tenantId") String tenantId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            @Param("judgeDays") int judgeDays);

    /**
     * 第二步：按配种ID回填分娩日期。同一配种多条分娩记录取<b>最早</b>，与原 cohort 的 MIN 口径一致。
     */
    @Update("UPDATE t_farm_farrowing_rate r "
        + "  LEFT JOIN (SELECT tenant_id, breeding_id, MIN(DATE(farrow_date)) AS d "
        + "               FROM t_farm_pig_farrow "
        + "              WHERE tenant_id = #{tenantId} AND del_flag = '0' AND breeding_id IS NOT NULL "
        + "              GROUP BY tenant_id, breeding_id) f "
        + "         ON f.tenant_id = r.tenant_id AND f.breeding_id = r.breeding_id "
        + "   SET r.farrow_date = f.d, r.update_time = NOW() "
        + " WHERE r.tenant_id = #{tenantId} AND r.del_flag = '0' "
        + "   AND NOT (r.farrow_date <=> f.d) "
        + WIN_LEDGER)
    int refreshStep2Farrow(@Param("tenantId") String tenantId,
                           @Param("from") LocalDate from,
                           @Param("to") LocalDate to,
                           @Param("judgeDays") int judgeDays);

    /**
     * 第三步：按配种ID回填返空流日期（返情 R / 空怀 N / 流产 A 三类合称返空流，多条取最早）。
     */
    @Update("UPDATE t_farm_farrowing_rate r "
        + "  LEFT JOIN (SELECT tenant_id, related_breeding_id AS bid, MIN(DATE(abnormal_date)) AS d "
        + "               FROM t_farm_pig_abnormal "
        + "              WHERE tenant_id = #{tenantId} AND del_flag = '0' "
        + "                AND related_breeding_id IS NOT NULL "
        + "              GROUP BY tenant_id, related_breeding_id) a "
        + "         ON a.tenant_id = r.tenant_id AND a.bid = r.breeding_id "
        + "   SET r.abnormal_date = a.d, r.update_time = NOW() "
        + " WHERE r.tenant_id = #{tenantId} AND r.del_flag = '0' "
        + "   AND NOT (r.abnormal_date <=> a.d) "
        + WIN_LEDGER)
    int refreshStep3Abnormal(@Param("tenantId") String tenantId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("judgeDays") int judgeDays);

    /**
     * 第四步：按<b>猪只ID</b>回填死淘日期（{@code t_farm_status_record} 的 DIE/ELIMINATE，多条取最早）。
     *
     * <p>{@code t_farm_status_record} 是追加型事件日志，<b>没有 del_flag</b>，故不带软删条件。
     * 甲方原文就是「查询猪只ID」：一头猪只死一次，她名下每条配种记录都写上同一个死淘日期。
     * 本列不参与分娩率计算，只供对账。</p>
     */
    @Update("UPDATE t_farm_farrowing_rate r "
        + "  LEFT JOIN (SELECT tenant_id, pig_id, MIN(DATE(change_time)) AS d "
        + "               FROM t_farm_status_record "
        + "              WHERE tenant_id = #{tenantId} AND event_type IN ('DIE','ELIMINATE') "
        + "              GROUP BY tenant_id, pig_id) s "
        + "         ON s.tenant_id = r.tenant_id AND s.pig_id = r.pig_id "
        + "   SET r.cull_date = s.d, r.update_time = NOW() "
        + " WHERE r.tenant_id = #{tenantId} AND r.del_flag = '0' "
        + "   AND NOT (r.cull_date <=> s.d) "
        + WIN_LEDGER)
    int refreshStep4Cull(@Param("tenantId") String tenantId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         @Param("judgeDays") int judgeDays);

    /**
     * 配种记录被软删 → 本表对应行跟着软删，否则分母里永久留着一条已撤销的配种。
     *
     * <p>本表是纯派生表，行在不在只由源表决定。用软删而非物理删，配合
     * {@link #refreshStep1Breeding} 的 {@code del_flag='0'} 支持复活。
     * 窗口圈台账的 {@code breeding_date}，与第一步同一个日期空间。</p>
     */
    @Update("UPDATE t_farm_farrowing_rate r "
        + "  LEFT JOIN t_farm_pig_breeding b "
        + "         ON b.id = r.breeding_id AND b.tenant_id = r.tenant_id AND b.del_flag = '0' "
        + "   SET r.del_flag = '1', r.update_time = NOW() "
        + " WHERE r.tenant_id = #{tenantId} AND r.del_flag = '0' "
        + WIN_LEDGER
        + "   AND b.id IS NULL")
    int softDeleteOrphans(@Param("tenantId") String tenantId,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to,
                          @Param("judgeDays") int judgeDays);

    /**
     * 分娩率取数 —— mp 月/年分娩率与月表/年表落盘共用的同一份实现
     * （甲方 2026-09-18 拍板 D-0090 + D-0091）。
     *
     * <p>分母（D-0090）：预估分娩日落在 {@code [from, to)} <b>且已到</b>（{@code ≤ asOf} 收口日）的记录数；
     * 预估分娩日尚未到达的批次不进分母 —— 它们的结局物理上还不可能产生。<br>
     * 分子（D-0091）：其中 {@code farrow_date} 非空 <b>且 {@code farrow_date ≤ expected_farrow_date}}</b>
     * 的记录数；甲方原话「对于晚产窝不算分子」。{@code farrowLate} 单独返回，只作对账不进分子。</p>
     *
     * <p>⚠️ 「唯一实现」只在 mp 这条线上成立：admin「配种批次对账」页另有一份按<b>配种月</b>分组的
     * 分娩率（{@code DashboardServiceImpl#getCohortLedger}），那是 D-0072 明确保留的口径，不走本方法。</p>
     *
     * @param asOf 收口日（传 T-1，与日表/月表/年表统一）
     * @return {denom, numer, farrowLate}
     */
    @Select("SELECT COUNT(*) AS " + K_DENOM + ", "
        + "        COALESCE(SUM(farrow_date IS NOT NULL "
        + "                 AND farrow_date <= expected_farrow_date), 0) AS " + K_NUMER + ", "
        + "        COALESCE(SUM(farrow_date IS NOT NULL "
        + "                 AND farrow_date >  expected_farrow_date), 0) AS " + K_FARROW_LATE + " "
        + "   FROM t_farm_farrowing_rate "
        + "  WHERE tenant_id = #{tenantId} AND del_flag = '0' "
        + "    AND expected_farrow_date >= #{from} "
        + "    AND expected_farrow_date <  #{to} "
        + "    AND expected_farrow_date <= #{asOf}")
    Map<String, Object> selectFarrowRate(@Param("tenantId") String tenantId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to,
                                         @Param("asOf") LocalDate asOf);

    /**
     * 配置漂移探测：本表里预估分娩日与「配种日 + 当前 judgeDays」对不上的行数。
     *
     * <p>{@code expected_farrow_date} 是落盘快照，每日任务只刷窗口内的行，
     * 配置改了窗口外的历史行不会跟着变 —— 返回 &gt;0 即提示重跑覆盖那些月份，不静默纠偏。</p>
     */
    @Select("SELECT COUNT(*) FROM t_farm_farrowing_rate "
        + " WHERE tenant_id = #{tenantId} AND del_flag = '0' "
        + "   AND expected_farrow_date <> breeding_date + INTERVAL #{judgeDays} DAY")
    int countJudgeDaysDrift(@Param("tenantId") String tenantId,
                            @Param("judgeDays") int judgeDays);
}
