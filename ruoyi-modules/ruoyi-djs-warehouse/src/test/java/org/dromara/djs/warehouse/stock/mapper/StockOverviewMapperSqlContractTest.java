package org.dromara.djs.warehouse.stock.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StockOverviewMapper SQL 口径契约。
 *
 * <p>守「固定性汇总」（甲方 2026-08-06）：日/月汇总的驱动集合必须是<b>连续自然日/月</b>，
 * 不能退回「有流水的日/月」—— 后者会让「有存货但当期没动过」的日子整行消失
 * （实测生产：7/31 灌完期初库存后再没出入库，列表只剩 2026-07-30 一行，甲方报「8 月汇总没生成」）。</p>
 */
@Tag("local")
@Tag("dev")
@DisplayName("StockOverviewMapper SQL 口径契约")
class StockOverviewMapperSqlContractTest {

    /**
     * 取注解里的 SQL 原文并归一：压平空白 + 转小写 + <b>还原 XML 实体</b>。
     * 实体必须还原 —— {@code <script>} 里的比较符写成 {@code &lt;} / {@code &gt;}，
     * 不还原的话断言得跟着写实体，可读性差且容易写错。
     */
    private static String normalizedSql(String methodName, Class<?>... paramTypes) throws Exception {
        Method method = StockOverviewMapper.class.getMethod(methodName, paramTypes);
        Select select = method.getAnnotation(Select.class);
        return String.join(" ", select.value())
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("日汇总：驱动集合是连续自然日序列，不是「有流水的日」")
    void dailyDriverIsContinuousDateSeries() throws Exception {
        String sql = normalizedSql("selectDailyOverview", String.class, java.util.Date.class, java.util.Date.class);

        // 连续序列：起点 = 首条流水日，逐日 +1 生成
        assertThat(sql)
            .contains("min(date(flow_date)) as d0")
            .contains("date_add(r.d0, interval s.n day)");
        // 上界含今天 —— 否则「最后一条流水之后到今天」这段又会缺行
        assertThat(sql).contains("greatest(coalesce(max(date(flow_date)), curdate()), curdate())");
        // 回归防线：不得退回按「有流水的日」DISTINCT 驱动
        assertThat(sql).doesNotContain("select distinct date_format(flow_date, '%y-%m-%d') as statdate");
    }

    @Test
    @DisplayName("月汇总：驱动集合是连续自然月序列，不是「有流水的月」")
    void monthlyDriverIsContinuousMonthSeries() throws Exception {
        String sql = normalizedSql("selectMonthlyOverview", String.class, java.util.Date.class, java.util.Date.class);

        assertThat(sql)
            .contains("cast(date_format(min(f0.flow_date), '%y-%m-01') as date) as m0")
            .contains("date_add(r.m0, interval s.n month)");
        // 回归防线：不得退回按「有流水的月」DISTINCT 驱动
        assertThat(sql).doesNotContain("select distinct date_format(f0.flow_date, '%y-%m') as statmonth");
    }

    @Test
    @DisplayName("序列不用递归 CTE —— cte_max_recursion_depth 默认 1000，超了是报错不是截断")
    void seriesAvoidsRecursiveCte() throws Exception {
        String daily = normalizedSql("selectDailyOverview", String.class, java.util.Date.class, java.util.Date.class);
        String monthly = normalizedSql("selectMonthlyOverview", String.class, java.util.Date.class, java.util.Date.class);

        assertThat(daily).doesNotContain("with recursive");
        assertThat(monthly).doesNotContain("with recursive");
        // 数字表：日 4 层（10000 天 ≈ 27 年）/ 月 3 层（1000 月 ≈ 83 年）
        assertThat(daily).contains("u.n + v.n * 10 + w.n * 100 + x.n * 1000 as n");
        assertThat(monthly).contains("u.n + v.n * 10 + w.n * 100 as n");
    }

    @Test
    @DisplayName("月列表 productCount 与月详情同集合：都按 (产品,库位) 分组 + 期初/入库/出库任一非 0")
    void monthlyListCountMatchesDetailSet() throws Exception {
        String list = normalizedSql("selectMonthlyOverview", String.class, java.util.Date.class, java.util.Date.class);
        String detail = normalizedSql("selectMonthlyDetail", String.class, java.util.Date.class, String.class, Long.class);

        assertThat(list).contains("group by mm.statmonth, mm.mstart, f.product_id, f.warehouse_id");
        assertThat(detail).contains("group by f.product_id, f.warehouse_id");
        // 两边都排除「期初=入库=出库=0」的噪声行
        assertThat(list).contains("having not (");
        assertThat(detail).contains("where not (g.beginstock = 0 and g.inboundqty = 0 and g.outboundqty = 0)");
    }

    @Test
    @DisplayName("日/月汇总都排除 ship_out 与 product_id=0（row42 生产产品不入库）")
    void bothExcludeShipOutAndOrphanProduct() throws Exception {
        String daily = normalizedSql("selectDailyOverview", String.class, java.util.Date.class, java.util.Date.class);
        String monthly = normalizedSql("selectMonthlyOverview", String.class, java.util.Date.class, java.util.Date.class);

        assertThat(daily).contains("s.flow_type <> 'ship_out'").contains("s.product_id <> 0");
        assertThat(monthly).contains("f.flow_type <> 'ship_out'").contains("f.product_id <> 0");
    }
}
