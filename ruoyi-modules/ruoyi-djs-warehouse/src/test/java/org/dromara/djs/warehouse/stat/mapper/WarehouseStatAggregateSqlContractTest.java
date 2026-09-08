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
 * （少个 cohort 过滤、把出品率分母换回接收重量、白条总重改读会缩水的在制品表、外购子查询漏了、
 * 外购猪被重复计）就当场红。</p>
 *
 * <p>只做纯字符串断言（不连库、不解析执行计划），因为契约就是「这些 SQL 里必须有这些语义片段」。</p>
 *
 * @author djs
 * @since V6-R172
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

    /**
     * 甲方 2026-09-08 口径：「白条总重：当日入白条库的产品总重，只算半扇和整只的重量」
     * 「白条均重：白条总重/当日入白条库的猪只耳号数量（需要去重）」。
     *
     * <p>三条硬约束钉在 SQL 上：① 源是入库<b>流水</b>不是在制品表（可复现）；
     * ② 「半扇和整只」= 产品类别 white_bar，不是产品名、不是库位；③ 分母是猪只去重数不是行数。</p>
     */
    @Test
    @DisplayName("白条总重 = 当日入白条库的白条产品（belong_type=white_bar）入库量之和，读不可变入库流水")
    void whiteBarInAggReadsImmutableInboundFlow() throws Exception {
        String sql = select("selectWhiteBarInAgg", String.class, String.class);
        assertThat(sql).as("源必须是入库流水（可复现），不是会被下游扣减/软删的在制品池")
            .contains("t_warehouse_stock_flow")
            .contains("f.inout_type = 'IN'");
        assertThat(sql).as("「只算半扇和整只」= 产品类别 white_bar，不绑产品名")
            .contains("p.belong_type = 'white_bar'")
            .doesNotContain("半扇")
            .doesNotContain("整只");
        assertThat(sql).as("按入库当天分桶").contains("DATE(f.flow_date) = #{statDate}");
        assertThat(sql).as("白条总重 = Σ 入库量").contains("COALESCE(SUM(t.weight), 0) AS barTotalWeight");
    }

    /**
     * 白条入库只认<b>燎毛产出</b>这一条通道。缺了这个白名单，门店退回 / 期初 / 采购入库 的半扇
     * 也会被当成「当日入白条库」计进分子 —— 而那几类流水的 {@code ear_no} 与 {@code white_bar_no}
     * <b>都是 NULL</b>（staging 实测 100%），{@code COUNT(DISTINCT NULL) = 0} 不给分母贡献任何一头猪：
     * 分子涨、分母不涨，极端情况某天只有这种行时 {@code barPigCount = 0}，页面会出现
     * 「白条总重非 0、白条均重 0.00」的自相矛盾。谁把这个条件删掉，这里当场红。
     */
    @Test
    @DisplayName("白条入库只认燎毛产出 slaughter_burn：退回 / 期初 / 采购入库的半扇不进白条总重")
    void whiteBarInAggOnlyCountsSlaughterBurn() throws Exception {
        String sql = select("selectWhiteBarInAgg", String.class, String.class);
        assertThat(sql).as("必须显式限定燎毛产出通道，不能只判 inout_type='IN'")
            .contains("f.flow_type = 'slaughter_burn'");
    }

    /**
     * 白条均重的分母是<b>猪只</b>去重数：一头猪出两扇 = 两行流水只能算 1 头。
     * 外购猪没有耳号（bar_info.ear_no 恒 NULL），必须退到白条 id，否则它的重量进分子不进分母、
     * 均重被抬高。相关子查询而非 JOIN —— JOIN 撞重复行会把 change_quantity 乘出多份。
     */
    @Test
    @DisplayName("白条均重分母 = 猪只去重数（耳号优先，外购无耳号退白条 id），且不用 JOIN 放大分子")
    void whiteBarPigCountDedupesByPig() throws Exception {
        String sql = select("selectWhiteBarInAgg", String.class, String.class);
        assertThat(sql).as("分母是去重猪只数，不是流水行数")
            .contains("COUNT(DISTINCT t.pigKey) AS barPigCount")
            .doesNotContain("COUNT(*) AS barPigCount");
        assertThat(sql).as("自养按耳号去重").contains("COALESCE(f.ear_no");
        assertThat(sql).as("外购无耳号 → 退该产出行所属白条（一头猪）")
            .contains("SELECT ih.white_bar_id")
            .contains("ih.white_bar_no = f.white_bar_no");
        assertThat(sql).as("产出行事后被软删也要查得到，故不带 del_flag 条件")
            .doesNotContain("ih.del_flag");
        assertThat(sql).as("白条身份用相关子查询取，JOIN 会把入库量乘出多份")
            .doesNotContain("JOIN t_warehouse_product_inhouse");
    }

    @Test
    @DisplayName("处理完成 cohort 已降为纯诊断：只出头数 + 接收重量之和，不再产出任何白条口径的量")
    void finishedAggIsDiagnosticOnly() throws Exception {
        String sql = select("selectFinishedAgg", String.class, String.class);
        assertThat(sql).contains("t_warehouse_bar_info");
        assertThat(sql).as("按处理完成时刻分桶").contains("DATE(b.finish_time) = #{statDate}");
        assertThat(sql).as("接收重量之和仍落盘（诊断列）")
            .contains("COALESCE(SUM(b.arrive_weight), 0) AS finishedArriveWeight");
        assertThat(sql).as("白条总重已改由 selectWhiteBarInAgg 一处定义，这里不许再算一份")
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
