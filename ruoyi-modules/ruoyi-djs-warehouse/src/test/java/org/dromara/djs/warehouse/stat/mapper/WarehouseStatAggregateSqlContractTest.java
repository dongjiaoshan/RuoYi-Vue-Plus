package org.dromara.djs.warehouse.stat.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WarehouseStatAggregateMapper} 猪肉段三 cohort SQL 口径契约（V6-R172）。
 *
 * <p>{@code WarehouseStatServiceImplTest} 把这几个 @Select 全 mock 了，只验了除法；R172 的口径全在
 * 被 mock 掉的 SQL 的 WHERE / CASE 里。本类反射取 @Select 原文、逐条钉关键片段，谁把口径 SQL 改错
 * （少个 cohort 过滤、把出品率分母换回接收重量、外购子查询漏了、外购猪被重复计）就当场红。</p>
 *
 * <p>只做纯字符串断言（不连库、不解析执行计划），因为契约就是「这些 SQL 里必须有这些语义片段」。</p>
 *
 * @author djs
 * @since V6-R172
 */
@Tag("local")
@Tag("dev")
@DisplayName("WarehouseStatAggregateMapper 猪肉段三 cohort SQL 口径契约（V6-R172）")
class WarehouseStatAggregateSqlContractTest {

    private static String select(String method, Class<?>... paramTypes) throws Exception {
        Method m = WarehouseStatAggregateMapper.class.getMethod(method, paramTypes);
        Select select = m.getAnnotation(Select.class);
        assertThat(select).as(method + " 必须带 @Select").isNotNull();
        // 归一空白，避免换行/缩进干扰片段匹配
        return String.join(" ", select.value()).replaceAll("\\s+", " ").trim();
    }

    @Test
    @DisplayName("屠宰头数 = 送宰 cohort：自养按 marketing_time + buy_date IS NULL，外购按 outsource_pig.slaughter_date")
    void countSlaughterUsesMarketingCohort() throws Exception {
        String sql = select("countSlaughter", String.class, String.class);
        assertThat(sql).contains("t_warehouse_bar_info");
        assertThat(sql).as("自养送宰必须排除外购镜像行").contains("buy_date IS NULL");
        assertThat(sql).as("自养以出栏事件写的 marketing_time 作送宰锚点").contains("DATE(marketing_time) = #{statDate}");
        assertThat(sql).as("外购生猪按送宰日计入").contains("t_warehouse_outsource_pig");
        assertThat(sql).contains("DATE(slaughter_date) = #{statDate}");
    }

    /**
     * 客户口径「外购的也计算送宰头数」的落地保证：外购猪录入时往 bar_info 镜像一行带 buy_date，
     * 若自养侧不挡 buy_date、或两侧用 JOIN 而非「分别 COUNT 再相加」，同一头外购猪就会被计两次。
     */
    @Test
    @DisplayName("同一头外购猪只计一次：自养侧 buy_date IS NULL 挡镜像行 + 外购侧独立 COUNT 相加（不 JOIN）")
    void outsourcePigCountedExactlyOnce() throws Exception {
        String count = select("countSlaughter", String.class, String.class);
        assertThat(count).as("自养侧必须在同一个 WHERE 里既挡镜像行又按送宰日分桶")
            .contains("buy_date IS NULL AND DATE(marketing_time) = #{statDate}");
        assertThat(count).as("外购头数来自 outsource_pig 的独立子查询、与自养相加")
            .contains("+ (SELECT COUNT(*) FROM t_warehouse_outsource_pig");
        assertThat(count).as("不能 JOIN——bar_id 无唯一约束，撞重复台账行会把同一头猪乘出多份")
            .doesNotContain("JOIN");

        String selfWeight = select("sumMarketingWeight", String.class, String.class);
        assertThat(selfWeight).as("送宰总重自养侧同样要挡外购镜像行").contains("buy_date IS NULL");
        assertThat(selfWeight).as("自养侧不得掺 outsource_pig，否则外购重量加两遍")
            .doesNotContain("t_warehouse_outsource_pig");

        String outWeight = select("sumOutsourceWeight", String.class, String.class);
        assertThat(outWeight).as("外购重量只从 outsource_pig 出、按送宰日")
            .contains("t_warehouse_outsource_pig")
            .contains("DATE(slaughter_date) = #{statDate}");
    }

    @Test
    @DisplayName("接收重量 = 称重 cohort：in_method=1 + arrive 非空 + 按 COALESCE(arrive_time, in_time) 分桶")
    void sumArriveWeightUsesWeighCohort() throws Exception {
        String sql = select("sumArriveWeight", String.class, String.class);
        assertThat(sql).contains("t_warehouse_bar_info");
        assertThat(sql).contains("in_method = 1");
        assertThat(sql).contains("arrive_weight IS NOT NULL");
        assertThat(sql).as("称重锚是 arrive_time，兜底 in_time")
            .contains("DATE(COALESCE(arrive_time, in_time)) = #{statDate}");
    }

    @Test
    @DisplayName("屠宰率基数：只算有出栏重量子集（t.baseWeight IS NOT NULL）+ 外购经 bar_id 反查 outsource_pig")
    void slaughterRateBaseFiltersMissingBaseAndJoinsOutsource() throws Exception {
        String sql = select("selectSlaughterRateBase", String.class, String.class);
        assertThat(sql).as("取不到出栏重量的猪必须从分子分母同时剔除")
            .contains("t.baseWeight IS NOT NULL");
        assertThat(sql).as("外购生猪出栏重量取 outsource_pig.pig_weight")
            .contains("t_warehouse_outsource_pig");
        assertThat(sql).contains("op.bar_id = b.bar_id");
        assertThat(sql).as("自养走 marketing_weight").contains("marketing_weight");
        assertThat(sql).as("与接收重量同一称重 cohort")
            .contains("DATE(COALESCE(b.arrive_time, b.in_time)) = #{statDate}");
    }

    @Test
    @DisplayName("处理完成 cohort：按 finish_time 分桶；白条总重 = Σ in_weight（整 cohort，一行=一头猪/耳号）")
    void finishedAggUsesFinishTimeCohort() throws Exception {
        String sql = select("selectFinishedAgg", String.class, String.class);
        assertThat(sql).contains("t_warehouse_bar_info");
        assertThat(sql).as("按处理完成时刻分桶").contains("DATE(b.finish_time) = #{statDate}");
        assertThat(sql).as("白条总重是整 cohort 的 in_weight 之和")
            .contains("COALESCE(SUM(b.in_weight), 0) AS barTotalWeight");
        assertThat(sql).as("接收重量之和仍落盘（诊断列）")
            .contains("COALESCE(SUM(b.arrive_weight), 0) AS finishedArriveWeight");
    }

    /**
     * 出品率分母**不由本查询产出**。甲方 2026-09-07 把需求原文改成
     * 「白条出品率：白条总重 / 完成接收重量的猪只出栏重量之和」，与上一行屠宰率的分母逐字相同，
     * 答复里也写明「分母错误，也是【完成接收重量的猪只出栏重量之和】」。
     * 谁再把分母塞回处理完成 cohort，这里当场红。
     */
    @Test
    @DisplayName("出品率分母不在处理完成 cohort 里算 —— 与屠宰率共用称重 cohort 的分母（甲方 2026-09-07 改稿）")
    void finishedAggDoesNotComputeYieldDenominator() throws Exception {
        String sql = select("selectFinishedAgg", String.class, String.class);
        assertThat(sql).as("处理完成 cohort 只出头数/白条总重/接收重量三个量")
            .doesNotContain("barYieldBaseWeight")
            .doesNotContain("barYieldNumerWeight");
        assertThat(sql).as("不再按出栏重量取子集，故无需反查外购台账")
            .doesNotContain("t_warehouse_outsource_pig")
            .doesNotContain("marketing_weight");
    }

    /**
     * 同一头外购猪可能挂着多条台账（先错录后补录）。子查询只取一行，必须定序，
     * 否则取哪一行由物理顺序决定：同一份数据换个存储顺序，出品率分母就变，
     * 还会出现「送宰总重按 A 行、出品率分母按 B 行」的行内自相矛盾。
     * 定序口径与送宰侧对齐：有送宰日期的优先（那才是被计进送宰总重的那条），再按 id 取最早。
     */
    @Test
    @DisplayName("外购台账重复行：取值必须定序（有送宰日期优先 + id），不能裸 LIMIT 1")
    void outsourceWeightSubqueryIsDeterministic() throws Exception {
        for (String m : new String[]{"selectSlaughterRateBase"}) {
            String sql = select(m, String.class, String.class);
            assertThat(sql).as(m + " 的外购子查询必须带 ORDER BY，裸 LIMIT 1 结果不确定")
                .contains("ORDER BY (op.slaughter_date IS NULL), op.id LIMIT 1");
        }
    }

    @Test
    @DisplayName("月表：出品率 = Σbar_yield_numer_weight / Σbar_yield_base_weight（与日率同口径）")
    void monthlyUsesYieldCohortBaseColumns() throws Exception {
        String sql = select("sumMonthlyFromDaily", String.class, String.class);
        assertThat(sql).as("屠宰率月分子/分母走 cohort 基数列")
            .contains("SUM(slaughter_rate_arrive_weight)")
            .contains("SUM(slaughter_rate_base_weight)");
        assertThat(sql).as("出品率月分子必须是 bar_yield_numer_weight，不能是 bar_total_weight")
            .contains("SUM(bar_yield_numer_weight)");
        assertThat(sql).as("出品率月分母必须是 bar_yield_base_weight（Σ出栏重量）")
            .contains("SUM(bar_yield_base_weight)");
        assertThat(sql).as("finished_arrive_weight 已降为诊断列，不再进月率")
            .doesNotContain("SUM(finished_arrive_weight)");
    }
}
