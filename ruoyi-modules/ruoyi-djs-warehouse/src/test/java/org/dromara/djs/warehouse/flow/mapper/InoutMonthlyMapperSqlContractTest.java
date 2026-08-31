package org.dromara.djs.warehouse.flow.mapper;

import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.flow.domain.query.InoutSummaryQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InoutMonthlyMapper} SQL 口径契约（V6-R154 / R155 / R156）。
 *
 * <p>锁四条会被"顺手改坏"的口径：</p>
 * <ol>
 *   <li><b>分组键 = 页面展示的那几列</b>，不含 product_id：重复产品档案（名称/类型/规格/单位全同）
 *       必须合并成一行；同时类型 / 规格 / 单位在键里，单位不同的同名产品不得被合并。</li>
 *   <li>甲方「供应商字段为空的，就统计到一起」→ 供应商名归一到 COALESCE(sp.supplier_name, '')；
 *       出库去向同理 COALESCE(stock_out_dest, '')。</li>
 *   <li>量必须用绝对值列 change_quantity 求和，不能用带符号的 change_num（部分写入方符号写反）。</li>
 *   <li>聚合 SQL 必须自带 tenant_id + del_flag（多租户拦截器对聚合不保证注入）。</li>
 * </ol>
 */
@Tag("local")
@Tag("dev")
@DisplayName("InoutMonthlyMapper SQL 口径契约（V6-R154/R155/R156）")
class InoutMonthlyMapperSqlContractTest {

    /**
     * 取注解里的 SQL 原文并归一：压平空白 + 转小写 + 还原 XML 实体。
     */
    private static String normalizedSql(String methodName, Class<?>... paramTypes) throws Exception {
        Method method = InoutMonthlyMapper.class.getMethod(methodName, paramTypes);
        Select select = method.getAnnotation(Select.class);
        return String.join(" ", select.value())
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    private static String inSql() throws Exception {
        return normalizedSql("selectInSummary", String.class, InoutSummaryQuery.class, List.class);
    }

    private static String outSql() throws Exception {
        return normalizedSql("selectOutSummary", String.class, InoutSummaryQuery.class, List.class);
    }

    private static String monthsSql() throws Exception {
        return normalizedSql("selectMonths", String.class, String.class, List.class, List.class);
    }

    @Test
    @DisplayName("入库汇总：按展示列聚合（名称+类型+规格+单位+入库方式+供应商），供应商为空统计到一起")
    void inSummaryGroupsByDisplayColumns() throws Exception {
        String sql = inSql();
        assertThat(sql).contains(
            "group by pi.product_name, pi.product_type, coalesce(pi.product_spec, ''), "
                + "coalesce(pi.product_unit, ''), f.flow_type, coalesce(sp.supplier_name, '')");
        assertThat(sql).contains("f.inout_type = 'in'");
    }

    @Test
    @DisplayName("出库汇总：按展示列聚合（名称+类型+规格+单位+出库去向），去向为空归一到同一桶")
    void outSummaryGroupsByDisplayColumns() throws Exception {
        String sql = outSql();
        assertThat(sql).contains(
            "group by pi.product_name, pi.product_type, coalesce(pi.product_spec, ''), "
                + "coalesce(pi.product_unit, ''), coalesce(f.stock_out_dest, '')");
        assertThat(sql).contains("f.inout_type = 'ot'");
    }

    @Test
    @DisplayName("重复产品档案必须合并：两个汇总都不得把 product_id 当分组键")
    void summariesNeverGroupByProductId() throws Exception {
        for (String sql : List.of(inSql(), outSql())) {
            String groupBy = sql.substring(sql.indexOf("group by"));
            assertThat(groupBy).doesNotContain("f.product_id");
            assertThat(groupBy).doesNotContain("coalesce(f.supplier_id");
        }
    }

    @Test
    @DisplayName("单位 / 规格 / 类型必须在分组键里：同名不同单位的产品不得被合并")
    void summariesKeepUnitSpecTypeApart() throws Exception {
        for (String sql : List.of(inSql(), outSql())) {
            String groupBy = sql.substring(sql.indexOf("group by"));
            assertThat(groupBy).contains("pi.product_name");
            assertThat(groupBy).contains("pi.product_type");
            assertThat(groupBy).contains("coalesce(pi.product_spec, '')");
            assertThat(groupBy).contains("coalesce(pi.product_unit, '')");
        }
    }

    @Test
    @DisplayName("ONLY_FULL_GROUP_BY：用了 COALESCE 的列，SELECT 与 GROUP BY 必须同一表达式")
    void coalescedColumnsAreSelectedAsSameExpression() throws Exception {
        for (String sql : List.of(inSql(), outSql())) {
            assertThat(sql).contains("coalesce(pi.product_spec, '') as productspec");
            assertThat(sql).contains("coalesce(pi.product_unit, '') as productunit");
            // 裸列写法会被 ONLY_FULL_GROUP_BY 判非法（分组键是表达式而非该列）
            assertThat(sql).doesNotContain("pi.product_spec as productspec");
            assertThat(sql).doesNotContain("pi.product_unit as productunit");
        }
        assertThat(inSql()).contains("coalesce(sp.supplier_name, '') as suppliername");
    }

    @Test
    @DisplayName("量一律 SUM(change_quantity)，不得用带符号的 change_num")
    void sumsAbsoluteQuantityColumn() throws Exception {
        for (String sql : List.of(inSql(), outSql())) {
            assertThat(sql).contains("sum(f.change_quantity)");
            assertThat(sql).doesNotContain("change_num");
        }
    }

    @Test
    @DisplayName("聚合 SQL 自带 tenant_id + del_flag（拦截器对聚合不保证注入）")
    void aggregatesCarryTenantAndDelFlag() throws Exception {
        for (String sql : List.of(inSql(), outSql(), monthsSql())) {
            assertThat(sql).contains("f.tenant_id = #{tenantid}");
            assertThat(sql).contains("f.del_flag = '0'");
        }
    }

    @Test
    @DisplayName("月份列表：有流水的月份才出行、按月倒序，且入/出各自套展示排除清单")
    void monthsListIsFlowDrivenAndDesc() throws Exception {
        String sql = monthsSql();
        assertThat(sql).contains("group by date_format(f.flow_date, '%y-%m')");
        assertThat(sql).contains("order by statmonth desc");
        assertThat(sql).contains("collection=\"inexcluded\"");
        assertThat(sql).contains("collection=\"outexcluded\"");
        // 本页统计流量，刻意不补零月（与「库存月汇总」的连续自然月序列分道）
        assertThat(sql).doesNotContain("interval s.n month");
    }
}
