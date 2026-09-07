package org.dromara.djs.warehouse.demand.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.demand.core.StoreDemandStatusMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mp 门店日卡三个桶（{@code arrivedCount / shippedCount / confirmedCount}）与<b>行级</b>门店态口径
 * 同源的契约（V6-R197）。
 *
 * <p>为什么必须钉：R197 把行级派生态改成「看到店量」，日卡分桶 SQL 却还按
 * {@code demand_status + received_time} 分，于是同一条需求 —— 日卡说「已发货」、点进按天明细每一行
 * 却是「已确认」。独立 QA 在 store C / 2026-09-09 复现过。修法是让分桶 CASE
 * <b>复用 {@link StoreDemandStatusMapping} 的同一份常量</b>而不是手写第二份条件；
 * 本类断言的就是「用的确实是同一份」——注解 SQL 里的条件 service 单测的 mock 打不到，只能拿原文钉。</p>
 *
 * <p>桶 ↔ 行级派生态对照（同 {@code StoreDemandAppletServiceImpl#dayStatus} 的 javadoc）：</p>
 * <table>
 *   <tr><th>行级派生态</th><th>日桶</th></tr>
 *   <tr><td>ARRIVED</td><td>arrivedCount</td></tr>
 *   <tr><td>SHIPPED + PARTIAL_ARRIVED</td><td>shippedCount</td></tr>
 *   <tr><td>CONFIRMED（含已发货态但到店量 = 0）</td><td>confirmedCount</td></tr>
 *   <tr><td>SUBMITTED / 其它</td><td>不进任何桶（service 的「其余」桶把当天压回最保守一档）</td></tr>
 * </table>
 */
@Tag("local")
@Tag("dev")
@DisplayName("mp 日卡分桶 SQL 与行级门店态同源契约（V6-R197）")
class StoreDemandDayBucketSqlContractTest {

    /** 取注解原文并归一：反转义 XML 实体 + 压掉换行/多空格（注解里是排版过的多行 SQL）。 */
    private static String dayPageSql() throws Exception {
        Method method = DemandManageMapper.class.getMethod("selectStoreDemandDayPage",
            IPage.class, Long.class, LocalDate.class, LocalDate.class);
        Select select = method.getAnnotation(Select.class);
        assertThat(select).as("selectStoreDemandDayPage 应带 @Select").isNotNull();
        return normalize(String.join(" ", select.value()));
    }

    /**
     * 归一：反转义 XML 实体 + 压空白 + 抹平 {@code IN (} / {@code IN(} 的差异。
     *
     * <p>后者纯排版差异：注解 SQL 里常量是直接拼在 {@code IN} 后面的（{@code IN(...)}），
     * {@code sqlPredicate} 里带一个空格（{@code IN (...)}）。比的是口径不是空格。</p>
     */
    private static String normalize(String sql) {
        return sql.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replaceAll("\\s+", " ")
            .replace("IN (", "IN(")
            .trim();
    }

    @Test
    @DisplayName("到店量子查询：日卡 CASE 与 sqlPredicate 用的是同一份片段（只有外层锚点不同）")
    void arrivedQtySubqueryIsTheSameFragment() throws Exception {
        String sql = dayPageSql();

        // 日卡侧锚主表别名 dm
        String dmFragment = normalize(StoreDemandStatusMapping.ARRIVED_QTY_SUBQUERY_DM);
        assertThat(sql)
            .as("日卡分桶必须直接拼 ARRIVED_QTY_SUBQUERY_DM，不许手写第二份到店量条件")
            .contains(dmFragment);

        // 筛选侧锚全表名；两者除锚点外必须逐字相同 —— 否则就是两套口径
        String tableFragment = normalize(StoreDemandStatusMapping.ARRIVED_QTY_SUBQUERY_PREFIX
            + "t_warehouse_demand_manage.id" + StoreDemandStatusMapping.ARRIVED_QTY_SUBQUERY_SUFFIX);
        assertThat(normalize(StoreDemandStatusMapping.sqlPredicate("PARTIAL_ARRIVED")))
            .contains(tableFragment);
        assertThat(dmFragment.replace("pp.demand_id = dm.id", "ANCHOR"))
            .as("两个锚点版本除 demand_id 引用外必须逐字相同")
            .isEqualTo(tableFragment.replace("pp.demand_id = t_warehouse_demand_manage.id", "ANCHOR"));
    }

    @Test
    @DisplayName("shipped 桶 = 已发货态 且未收货 且到店量 > 0（= 行级 SHIPPED ∪ PARTIAL_ARRIVED）")
    void shippedBucketCountsArrivedGreaterThanZero() throws Exception {
        String sql = dayPageSql();

        assertThat(sql).as("状态集合必须复用 SHIPPED_STATUS_SQL 常量")
            .contains("dm.demand_status IN" + StoreDemandStatusMapping.SHIPPED_STATUS_SQL);
        assertThat(sql)
            .as("shipped 桶必须带「到店量 > 0」，否则缺量出车（一件没到）的天仍会显示已发货")
            .contains(normalize(StoreDemandStatusMapping.ARRIVED_QTY_SUBQUERY_DM + " > 0"))
            .contains("AS shippedCount");
        // shipped 合并了 SHIPPED 与 PARTIAL_ARRIVED，所以不该出现与需求量的比较（那是行级二分才需要的）
        assertThat(sql.substring(sql.indexOf("AS arrivedCount"), sql.indexOf("AS shippedCount")))
            .as("shipped 桶合并了已发货 + 部分到店，不需要也不该与 demand_quantity 比较")
            .doesNotContain("demand_quantity");
    }

    @Test
    @DisplayName("confirmed 桶 = sqlPredicate(CONFIRMED) 的同一套条件（含「已发货态但到店量 <= 0」那一支）")
    void confirmedBucketMatchesSqlPredicate() throws Exception {
        String sql = dayPageSql();

        assertThat(sql).as("状态集合必须复用 CONFIRMED_STATUS_SQL 常量")
            .contains("dm.demand_status IN" + StoreDemandStatusMapping.CONFIRMED_STATUS_SQL);
        assertThat(sql)
            .as("confirmed 桶必须收下「已发货态但到店量 = 0」的缺量出车行 —— 甲方原话「到店量等于 0 时状态还是已确认」")
            .contains(normalize(StoreDemandStatusMapping.ARRIVED_QTY_SUBQUERY_DM + " <= 0"))
            .contains("AS confirmedCount");
        // 与筛选侧同源：sqlPredicate(CONFIRMED) 也必须是「原确认态 OR 已发货态且到店量<=0」两支
        String confirmedPredicate = normalize(StoreDemandStatusMapping.sqlPredicate("CONFIRMED"));
        assertThat(confirmedPredicate)
            .contains("demand_status IN" + StoreDemandStatusMapping.CONFIRMED_STATUS_SQL)
            .contains("demand_status IN" + StoreDemandStatusMapping.SHIPPED_STATUS_SQL)
            .endsWith("<= 0))");
    }

    @Test
    @DisplayName("arrived 桶 = 已确认及之后 + received_time IS NOT NULL（与 sqlPredicate(ARRIVED) 同集合）")
    void arrivedBucketMatchesSqlPredicate() throws Exception {
        String sql = dayPageSql();

        assertThat(sql)
            .contains("dm.demand_status IN" + StoreDemandStatusMapping.CONFIRMED_OR_LATER_STATUS_SQL)
            .contains("AS arrivedCount");
        assertThat(normalize(StoreDemandStatusMapping.sqlPredicate("ARRIVED")))
            .as("筛选侧同样用 CONFIRMED_OR_LATER_STATUS_SQL，不是各写一串")
            .contains("demand_status IN" + StoreDemandStatusMapping.CONFIRMED_OR_LATER_STATUS_SQL)
            .contains("received_time IS NOT NULL");
    }

    @Test
    @DisplayName("三桶仍覆盖同一集合：只是把「已发货态未收货」按到店量重切，confirmRate 分子不变")
    void bucketsStillPartitionTheSameSet() throws Exception {
        String sql = dayPageSql();

        // shipped 与 confirmed 的「已发货态」分支互补：一个 > 0、一个 <= 0，且都要求 received_time IS NULL
        assertThat(sql).contains(normalize(StoreDemandStatusMapping.ARRIVED_QTY_SUBQUERY_DM + " > 0"));
        assertThat(sql).contains(normalize(StoreDemandStatusMapping.ARRIVED_QTY_SUBQUERY_DM + " <= 0"));
        int shippedBranchStart = sql.indexOf("AS arrivedCount");
        String shippedAndConfirmed = sql.substring(shippedBranchStart, sql.indexOf("AS confirmedCount"));
        assertThat(shippedAndConfirmed.split("dm\\.received_time IS NULL", -1).length - 1)
            .as("shipped 一处 + confirmed 两支里的已发货分支一处 + confirmed 的原确认分支一处 = 3 处未收货约束")
            .isEqualTo(3);
    }
}
