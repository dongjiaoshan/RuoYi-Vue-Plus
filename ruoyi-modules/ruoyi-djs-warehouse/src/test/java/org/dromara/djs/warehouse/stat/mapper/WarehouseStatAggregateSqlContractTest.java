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
 * （少个 cohort 过滤、把 D1 出品率分子的 arrive 非空条件删掉、外购子查询漏了）就当场红。</p>
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
    @DisplayName("屠宰头数 = 出栏 cohort：自养按 marketing_time + buy_date IS NULL，外购按 outsource_pig.slaughter_date")
    void countSlaughterUsesMarketingCohort() throws Exception {
        String sql = select("countSlaughter", String.class, String.class);
        assertThat(sql).contains("t_warehouse_bar_info");
        assertThat(sql).as("自养出栏必须排除外购镜像行").contains("buy_date IS NULL");
        assertThat(sql).as("自养按出栏时刻分桶").contains("DATE(marketing_time) = #{statDate}");
        assertThat(sql).as("外购生猪按送宰日计入").contains("t_warehouse_outsource_pig");
        assertThat(sql).contains("DATE(slaughter_date) = #{statDate}");
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
    @DisplayName("处理完成 cohort：按 finish_time 分桶；白条总重 = Σ in_weight（全 cohort）")
    void finishedAggUsesFinishTimeCohort() throws Exception {
        String sql = select("selectFinishedAgg", String.class, String.class);
        assertThat(sql).contains("t_warehouse_bar_info");
        assertThat(sql).as("按处理完成时刻分桶").contains("DATE(finish_time) = #{statDate}");
        assertThat(sql).as("白条总重是整 cohort 的 in_weight 之和").contains("SUM(in_weight)");
        assertThat(sql).as("接收重量之和是出品率分母").contains("SUM(arrive_weight)");
    }

    @Test
    @DisplayName("D1：出品率分子 = 处理完成 ∩ arrive 非空的 in_weight（与分母同子集，防 >100%）")
    void finishedAggYieldNumeratorIsSymmetricWithDenominator() throws Exception {
        String sql = select("selectFinishedAgg", String.class, String.class);
        // 出品率分子必须带 arrive 非空条件，否则未称重的完成猪只进分子不进分母 → 率破 100%
        assertThat(sql.replaceAll("\\s+", " "))
            .as("D1：出品率分子缺 arrive 非空过滤，会算出 >100% 的出品率")
            .contains("CASE WHEN arrive_weight IS NOT NULL THEN in_weight");
        assertThat(sql).contains("AS barYieldNumerWeight");
    }

    @Test
    @DisplayName("月表：出品率分子取 bar_yield_numer_weight（不是 bar_total_weight），分母 finished_arrive_weight")
    void monthlyUsesYieldNumerColumn() throws Exception {
        String sql = select("sumMonthlyFromDaily", String.class, String.class);
        assertThat(sql).as("屠宰率月分子/分母走 cohort 基数列")
            .contains("SUM(slaughter_rate_arrive_weight)")
            .contains("SUM(slaughter_rate_base_weight)");
        assertThat(sql).as("D1：出品率月分子必须是 bar_yield_numer_weight，不能是 bar_total_weight")
            .contains("SUM(bar_yield_numer_weight)");
        assertThat(sql).contains("SUM(finished_arrive_weight)");
    }
}
