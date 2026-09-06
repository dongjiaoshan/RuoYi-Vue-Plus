package org.dromara.djs.warehouse.flow.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.flow.domain.query.InoutStatQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InoutStatMapper} SQL 口径契约（V6-R167 出入库统计）。
 *
 * <p>锁甲方 row167 的三条统计口径 + 三条会被"顺手改坏"的实现口径：</p>
 * <ol>
 *   <li><b>分组维度</b>：入库 = 产品 × 入库方式 × 供应商，出库 = 产品 × 出库去向；
 *       两者都把类型 / 规格 / 单位一起放进键（重复产品档案合并、单位不同的同名产品不合并），
 *       且都不含 product_id。</li>
 *   <li><b>日期区间</b>：起点 {@code >= dateFrom}、终点 {@code < dateTo + 1 天}
 *       （flow_date 是 DATETIME，写 {@code <=} 会漏掉当天带时分秒的流水）。</li>
 *   <li><b>空供应商归并</b>：供应商名归一到 {@code COALESCE(sp.supplier_name, '')} 参与分组，
 *       且「无供应商」桶可被单独筛出（NULL 与空串都算）。</li>
 *   <li>量必须用绝对值列 change_quantity 求和，不能用带符号的 change_num。</li>
 *   <li>聚合 SQL 必须自带 tenant_id + del_flag（多租户拦截器对聚合不保证注入）。</li>
 *   <li><b>列表与导出必须是同一份 SQL</b>——甲方拿导出的表核对页面，两份各写一遍迟早改歪一边。</li>
 * </ol>
 */
@Tag("local")
@Tag("dev")
@DisplayName("InoutStatMapper SQL 口径契约（V6-R167）")
class InoutStatMapperSqlContractTest {

    /**
     * 取注解里的 SQL 原文并归一：压平空白 + 转小写 + 还原 XML 实体。
     */
    private static String normalizedSql(String methodName, Class<?>... paramTypes) throws Exception {
        Method method = InoutStatMapper.class.getMethod(methodName, paramTypes);
        Select select = method.getAnnotation(Select.class);
        return String.join(" ", select.value())
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    private static String inPageSql() throws Exception {
        return normalizedSql("selectInStatPage", IPage.class, String.class, InoutStatQuery.class, List.class);
    }

    private static String inListSql() throws Exception {
        return normalizedSql("selectInStatList", String.class, InoutStatQuery.class, List.class);
    }

    private static String outPageSql() throws Exception {
        return normalizedSql("selectOutStatPage", IPage.class, String.class, InoutStatQuery.class, List.class);
    }

    private static String outListSql() throws Exception {
        return normalizedSql("selectOutStatList", String.class, InoutStatQuery.class, List.class);
    }

    @Test
    @DisplayName("入库统计：按 产品 × 入库方式 × 供应商 聚合（类型/规格/单位同在键里）")
    void inStatGroupsByProductModeSupplier() throws Exception {
        String sql = inPageSql();
        assertThat(sql).contains(
            "group by pi.product_name, pi.product_type, coalesce(pi.product_spec, ''), "
                + "coalesce(pi.product_unit, ''), f.flow_type, coalesce(sp.supplier_name, '')");
        assertThat(sql).contains("f.inout_type = 'in'");
    }

    @Test
    @DisplayName("出库统计：按 产品 × 出库去向 聚合，去向为空归一到同一桶")
    void outStatGroupsByProductAndDest() throws Exception {
        String sql = outPageSql();
        assertThat(sql).contains(
            "group by pi.product_name, pi.product_type, coalesce(pi.product_spec, ''), "
                + "coalesce(pi.product_unit, ''), coalesce(f.stock_out_dest, '')");
        assertThat(sql).contains("f.inout_type = 'ot'");
    }

    @Test
    @DisplayName("重复产品档案必须合并：两个统计都不得把 product_id 当分组键")
    void statsNeverGroupByProductId() throws Exception {
        for (String sql : List.of(inPageSql(), outPageSql())) {
            String groupBy = sql.substring(sql.indexOf("group by"));
            assertThat(groupBy).doesNotContain("f.product_id");
            assertThat(groupBy).doesNotContain("coalesce(f.supplier_id");
        }
    }

    @Test
    @DisplayName("日期区间：起点 >= dateFrom，终点 < dateTo + 1 天（flow_date 是 DATETIME）")
    void dateRangeIsHalfOpenOnTheRight() throws Exception {
        for (String sql : List.of(inPageSql(), outPageSql())) {
            assertThat(sql).contains("f.flow_date >= #{query.datefrom}");
            assertThat(sql).contains("f.flow_date < date_add(#{query.dateto}, interval 1 day)");
            // <= dateTo 会漏掉当天 00:00 之后的流水，甲方按天选区间必然少统计最后一天
            assertThat(sql).doesNotContain("f.flow_date <= #{query.dateto}");
            // 区间两端都是可选条件（不填 = 不限）
            assertThat(sql).contains("test=\"query.datefrom != null\"");
            assertThat(sql).contains("test=\"query.dateto != null\"");
        }
    }

    @Test
    @DisplayName("空供应商归并：参与分组的是 COALESCE(supplier_name,'')，且「无供应商」桶能被单独筛出")
    void emptySupplierIsOneBucketAndFilterable() throws Exception {
        String sql = inPageSql();
        assertThat(sql).contains("coalesce(sp.supplier_name, '') as suppliername");
        assertThat(sql).contains("coalesce(sp.supplier_name, '')");
        // 供应商档案被删（LEFT JOIN 落空 → NULL）与流水本就没填（'' / NULL）必须落同一桶
        assertThat(sql).contains("(sp.supplier_name is null or sp.supplier_name = '')");
        // 选中具体供应商时按 id 精确匹配，不是名称 LIKE（与「入库记录」「入库汇总」同口径）
        assertThat(sql).contains("f.supplier_id = #{query.supplierid}");
        assertThat(sql).doesNotContain("like concat('%', #{query.suppliername}");
    }

    @Test
    @DisplayName("ONLY_FULL_GROUP_BY：用了 COALESCE 的列，SELECT 与 GROUP BY 必须同一表达式")
    void coalescedColumnsAreSelectedAsSameExpression() throws Exception {
        for (String sql : List.of(inPageSql(), outPageSql())) {
            assertThat(sql).contains("coalesce(pi.product_spec, '') as productspec");
            assertThat(sql).contains("coalesce(pi.product_unit, '') as productunit");
            // 裸列写法会被 ONLY_FULL_GROUP_BY 判非法（分组键是表达式而非该列）
            assertThat(sql).doesNotContain("pi.product_spec as productspec");
            assertThat(sql).doesNotContain("pi.product_unit as productunit");
        }
    }

    @Test
    @DisplayName("量一律 SUM(change_quantity)，不得用带符号的 change_num")
    void sumsAbsoluteQuantityColumn() throws Exception {
        for (String sql : List.of(inPageSql(), outPageSql())) {
            assertThat(sql).contains("sum(f.change_quantity)");
            assertThat(sql).doesNotContain("change_num");
        }
    }

    @Test
    @DisplayName("聚合 SQL 自带 tenant_id + del_flag（拦截器对聚合不保证注入）")
    void aggregatesCarryTenantAndDelFlag() throws Exception {
        for (String sql : List.of(inPageSql(), outPageSql())) {
            assertThat(sql).contains("f.tenant_id = #{tenantid}");
            assertThat(sql).contains("f.del_flag = '0'");
        }
    }

    @Test
    @DisplayName("分页与导出共用同一份 SQL：导出的表必须等于页面翻完的所有页")
    void pageAndExportShareTheSameSql() throws Exception {
        assertThat(inListSql()).isEqualTo(inPageSql());
        assertThat(outListSql()).isEqualTo(outPageSql());
    }

    @Test
    @DisplayName("外层只排序不聚合：MP 自动 count 数的是聚合后的行数；ORDER BY 取全分组键保证翻页稳定")
    void outerQueryIsOrderOnlySoPagingIsStable() throws Exception {
        String in = inPageSql();
        assertThat(in).contains(") g order by g.productname, g.producttype, g.productspec, "
            + "g.productunit, g.flowtype, g.suppliername");
        String out = outPageSql();
        assertThat(out).contains(") g order by g.productname, g.producttype, g.productspec, "
            + "g.productunit, g.stockoutdest");
    }
}
