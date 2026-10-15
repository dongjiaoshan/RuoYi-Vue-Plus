package org.dromara.djs.warehouse.stat.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WarehouseStatAggregateMapper} 猪肉段取数口径 SQL 契约。
 *
 * <p>{@code WarehouseStatServiceImplTest} 把这几个 @Select 全 mock 了，只验了除法；口径全在
 * 被 mock 掉的 SQL 的 WHERE / CASE 里。本类反射取 @Select 原文、逐条钉关键片段，谁把口径 SQL 改错
 * （分桶键换回送宰日/入库日、白条改读会缩水的在制品表、出品率分子分母不是同一批、外购子查询漏了）
 * 就当场红。</p>
 *
 * <p>只做纯字符串断言（不连库、不解析执行计划），因为契约就是「这些 SQL 里必须有这些语义片段」。</p>
 *
 * @author djs
 * @since V6-R172（V6-R284 改为接收 cohort）
 */
@Tag("local")
@Tag("dev")
@DisplayName("WarehouseStatAggregateMapper 猪肉段取数口径 SQL 契约")
class WarehouseStatAggregateSqlContractTest {

    private static String select(String method, Class<?>... paramTypes) throws Exception {
        Method m = WarehouseStatAggregateMapper.class.getMethod(method, paramTypes);
        Select select = m.getAnnotation(Select.class);
        assertThat(select).as(method + " 必须带 @Select").isNotNull();
        // 归一空白，避免换行/缩进干扰片段匹配
        return String.join(" ", select.value()).replaceAll("\\s+", " ").trim();
    }

    private static String cohort() throws Exception {
        return select("selectReceivedCohortAgg", String.class, String.class);
    }

    /**
     * 甲方行284：「屠宰头数调整成取燎毛间里的接收日期为核心日期，这头猪后续所有的指标都以这个日期进行计算」。
     * 猪肉段只剩一个分桶键 —— 接收日；送宰日 marketing_time、外购 slaughter_date、燎毛记录 burn_time、
     * 白条入库日 flow_date 都不许再出现在分桶条件里。
     */
    @Test
    @DisplayName("猪肉段唯一分桶键 = 燎毛间接收日 DATE(COALESCE(arrive_time, in_time))")
    void cohortBucketsByReceiveDateOnly() throws Exception {
        String sql = cohort();
        assertThat(sql).contains("FROM t_warehouse_bar_info b");
        assertThat(sql).as("按接收日分桶（arrive_time 为首个燎毛产品入库时刻，旧数据退 in_time）")
            .contains("DATE(COALESCE(b.arrive_time, b.in_time)) = #{statDate}");
        assertThat(sql).as("只算进过燎毛间且已有接收重量的猪")
            .contains("b.in_method = 1 AND b.arrive_weight IS NOT NULL");
        assertThat(sql).as("送宰日不再作分桶键").doesNotContain("DATE(marketing_time)").doesNotContain("DATE(b.marketing_time)");
        assertThat(sql).as("外购送宰日不再作分桶键").doesNotContain("DATE(slaughter_date)");
        assertThat(sql).as("燎毛记录日不再作分桶键").doesNotContain("burn_time");
        assertThat(sql).as("白条入库日不再作分桶键：第二个半扇隔天入库也算回接收那天")
            .doesNotContain("flow_date");
    }

    @Test
    @DisplayName("头数 = 接收 cohort 的行数（一行 bar_info = 一头猪），出栏/接收/白条三项各自 Σ")
    void cohortAggregatesPerPig() throws Exception {
        String sql = cohort();
        assertThat(sql).contains("COUNT(*) AS receivedCount");
        assertThat(sql).contains("COALESCE(SUM(t.baseWeight), 0) AS baseWeight");
        assertThat(sql).contains("COALESCE(SUM(t.arriveWeight), 0) AS arriveWeight");
        assertThat(sql).contains("COALESCE(SUM(t.barWeight), 0) AS barWeight");
        assertThat(sql).as("白条重量用相关子查询逐猪取，JOIN 流水会把 bar_info 行乘出多份、头数翻倍")
            .doesNotContain("JOIN t_warehouse_stock_flow");
    }

    /** D-0140：分子取整批接收/白条重量，分母 Σ 出栏重量天然跳过 NULL。 */
    @Test
    @DisplayName("出品率分子不因缺出栏重量而缩小")
    void rateNumeratorsUseWholeReceivedCohort() throws Exception {
        String sql = cohort();
        assertThat(sql).contains("COALESCE(SUM(t.arriveWeight), 0) AS rateArrive");
        assertThat(sql).contains("COALESCE(SUM(t.barWeight), 0) AS barYieldNumer");
        assertThat(sql).as("自养出栏重量").contains("WHEN b.buy_date IS NULL THEN b.marketing_weight");
        assertThat(sql).as("外购经 bar_id 反查外购台账").contains("op.bar_id = b.bar_id");
    }

    /**
     * 白条产品重量读燎毛产出入库流水（重量调整会就地改写这笔流水），只认 white_bar 类别与
     * slaughter_burn 通道（退回 / 期初 / 采购入库的半扇不算这头猪的产出）。
     * 在库表 product_inhouse 发走即软删、不能当产出记录；只拿它给无耳号流水找猪。
     */
    @Test
    @DisplayName("白条重量 = 这头猪的燎毛产出白条入库流水之和（耳号对猪，无耳号经 white_bar_no 找猪）")
    void barWeightReadsSlaughterBurnInboundFlowPerPig() throws Exception {
        String sql = cohort();
        assertThat(sql).contains("FROM t_warehouse_stock_flow f");
        assertThat(sql).contains("f.inout_type = 'IN'").contains("f.flow_type = 'slaughter_burn'");
        assertThat(sql).contains("p.belong_type = 'white_bar'").doesNotContain("半扇").doesNotContain("整只");
        assertThat(sql).as("有耳号按耳号对猪").contains("f.ear_no = b.ear_no");
        assertThat(sql).as("无耳号（外购）按白条号找回所属那头猪")
            .contains("ih.white_bar_no = f.white_bar_no")
            .contains("ih.white_bar_id = b.id");
        assertThat(sql).as("产出行发走后软删也要找得到，不带 del_flag").doesNotContain("ih.del_flag");
        assertThat(sql).as("不拿在库表的当前重量当产出").doesNotContain("SUM(ih.product_weight)");
    }

    @Test
    @DisplayName("处理完成 cohort 已降为纯诊断：只出头数 + 接收重量之和，不再产出任何白条口径的量")
    void finishedAggIsDiagnosticOnly() throws Exception {
        String sql = select("selectFinishedAgg", String.class, String.class);
        assertThat(sql).contains("t_warehouse_bar_info");
        assertThat(sql).as("按处理完成时刻分桶").contains("DATE(b.finish_time) = #{statDate}");
        assertThat(sql).as("接收重量之和仍落盘（诊断列）")
            .contains("COALESCE(SUM(b.arrive_weight), 0) AS finishedArriveWeight");
        assertThat(sql).as("白条总重由 selectReceivedCohortAgg 一处定义，这里不许再算一份")
            .doesNotContain("barTotalWeight")
            .doesNotContain("in_weight");
        assertThat(sql).as("出品率分子分母都不在这里算")
            .doesNotContain("barYieldBaseWeight")
            .doesNotContain("barYieldNumerWeight");
        assertThat(sql).as("不再按出栏重量取子集，故无需反查外购台账")
            .doesNotContain("t_warehouse_outsource_pig")
            .doesNotContain("marketing_weight");
    }

    /**
     * 同一头外购猪可能挂着多条台账（先错录后补录）。子查询只取一行，必须定序，
     * 否则取哪一行由物理顺序决定，出品率分母会随存储顺序变。
     * 定序口径：有送宰日期的优先，再按 id 取最早。
     */
    @Test
    @DisplayName("外购台账重复行：取值必须定序（有送宰日期优先 + id），不能裸 LIMIT 1")
    void outsourceWeightSubqueryIsDeterministic() throws Exception {
        assertThat(cohort()).as("外购子查询必须带 ORDER BY，裸 LIMIT 1 结果不确定")
            .contains("ORDER BY (op.slaughter_date IS NULL), op.id LIMIT 1");
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
