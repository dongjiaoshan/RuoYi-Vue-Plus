package org.dromara.djs.store.manage.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StoreManageMapper} SQL 口径契约（V6 row180）。
 *
 * <p>把甲方那几句口径钉成断言：改 SQL 时如果口径被顺手改掉，这里先红。</p>
 *
 * @author djs
 * @since MGMT-MP-STORE-MONTH-001
 */
@Tag("local")
@Tag("dev")
@DisplayName("StoreManageMapper SQL 口径契约")
class StoreManageMapperSqlContractTest {

    private static String normalizedSql(String methodName) throws Exception {
        return normalizedSql(methodName,
            String.class, Long.class, LocalDate.class, LocalDate.class, List.class);
    }

    private static String normalizedSql(String methodName, Class<?>... paramTypes) throws Exception {
        Method method = StoreManageMapper.class.getMethod(methodName, paramTypes);
        Select select = method.getAnnotation(Select.class);
        assertThat(select).as("@Select on %s", methodName).isNotNull();
        return String.join(" ", select.value()).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * 把 SQL 片段常量拆成可断言的单行（与 {@link #normalizedSql} 同一套归一）。
     *
     * @param fragment 片段常量
     * @return 归一后的非空片段
     */
    private static List<String> normalizeParts(String fragment) {
        return List.of(fragment.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT));
    }

    @Test
    @DisplayName("品类数 = 当月到店（inbound_qty>0）产品去重数，不是记录数")
    void arrivedProductCountIsDistinctProductId() throws Exception {
        String sql = normalizedSql("countArrivedProducts");

        assertThat(sql)
            .contains("count(distinct l.product_id) as signed) as productcount")
            .contains("t_store_daily_ledger")
            .contains("l.inbound_qty &gt; 0")
            .doesNotContain("count(*)");
    }

    @Test
    @DisplayName("需求量 = 门店下单量，排除 DELETED / CANCELLED / DRAFT，且只算门店单")
    void demandExcludesDraftAndCancelled() throws Exception {
        String sql = normalizedSql("sumDemandQty");

        assertThat(sql)
            .contains("t_warehouse_demand_manage")
            .contains("sum(d.demand_quantity)")
            .contains("d.demand_status not in ('deleted','cancelled','draft')")
            .contains("d.store_id is not null");
    }

    @Test
    @DisplayName("销售量 = 门店盘点的 销售数量 + 赠送量")
    void saleIsLedgerSalePlusGift() throws Exception {
        String sql = normalizedSql("sumSaleQty");

        assertThat(sql)
            .contains("t_store_daily_ledger")
            .contains("sum(l.sale_qty + l.gift_qty)");
    }

    @Test
    @DisplayName("退回量 = 门店退回记录，方向锁 store_to_warehouse（不含顾客退货）")
    void returnLocksStoreToWarehouseDirection() throws Exception {
        String sql = normalizedSql("sumReturnQty");

        assertThat(sql)
            .contains("t_store_return")
            .contains("sum(r.return_quantity)")
            .contains("r.return_direction = 'store_to_warehouse'")
            .doesNotContain("customer_to_store");
    }

    @Test
    @DisplayName("三个指标一律按产品主数据 product_unit 分组（同一把单位尺子）+ 月份区间左闭右开")
    void allMetricsGroupByProductUnitAndHalfOpenMonth() throws Exception {
        for (String m : List.of("sumDemandQty", "sumSaleQty", "sumReturnQty")) {
            String sql = normalizedSql(m);
            assertThat(sql).as(m + " 单位取产品主数据").contains("p.product_unit as unit");
            assertThat(sql).as(m + " 按业态+单位分组").contains("group by p.belong_type, p.product_unit");
            assertThat(sql).as(m + " 区间左闭").contains("&gt;= #{monthstart}");
            assertThat(sql).as(m + " 区间右开").contains("&lt; #{nextstart}");
        }
    }

    @Test
    @DisplayName("明细下钻与业态卡共用同一份 FROM / WHERE 片段（改一处两边同时生效）")
    void detailSharesConditionsWithCards() throws Exception {
        String detail = detailSql();

        for (String fragment : List.of(
            StoreManageMapper.DEMAND_FROM, StoreManageMapper.DEMAND_WHERE,
            StoreManageMapper.SALE_FROM, StoreManageMapper.SALE_WHERE,
            StoreManageMapper.RETURN_FROM, StoreManageMapper.RETURN_WHERE)) {
            for (String part : normalizeParts(fragment)) {
                assertThat(detail).as("明细 SQL 含片段 [%s]", part).contains(part);
            }
        }
        // 卡片侧同样引用这些常量（这三条已由上面各自的口径用例覆盖，这里只兜住「没被改成别的表」）
        assertThat(normalizedSql("sumDemandQty")).contains(normalizeParts(StoreManageMapper.DEMAND_WHERE).get(0));
        assertThat(normalizedSql("sumSaleQty")).contains(normalizeParts(StoreManageMapper.SALE_WHERE).get(0));
        assertThat(normalizedSql("sumReturnQty")).contains(normalizeParts(StoreManageMapper.RETURN_WHERE).get(0));
    }

    @Test
    @DisplayName("明细三源全外合并：UNION ALL 三支 + 按 productId 归并，只在退回里出现的产品也出行")
    void detailUnionsThreeSourcesAndMergesByProduct() throws Exception {
        String detail = detailSql();

        // 三支 union all（两个 union all 分隔符 = 三个分支）
        assertThat(detail.split("union all", -1)).as("UNION ALL 三个分支").hasSize(3);
        assertThat(detail).contains("group by g.productid");
        // 三个量各自只在自己那一支里求和，另两支补 0 → 缺席的源不会把整行挤掉
        assertThat(detail)
            .contains("coalesce(sum(d.demand_quantity), 0) as demandqty")
            .contains("coalesce(sum(l.sale_qty + l.gift_qty), 0) as saleqty")
            .contains("coalesce(sum(r.return_quantity), 0) as returnqty")
            .contains("0 as demandqty, 0 as saleqty");
        // 外层不带 GROUP BY，MP 自动 count 才能数出「合并后的产品行数」；排序末位补 productId 凑全序
        assertThat(detail).contains("order by t.demandqty desc, t.productname, t.productid");
        assertThat(detail.substring(detail.lastIndexOf(") t"))).doesNotContain("group by");
    }

    @Test
    @DisplayName("明细：当月三个量全 0 的产品不出行，且过滤在外层（分页 total 一起收窄）")
    void detailFiltersAllZeroProductRows() throws Exception {
        String detail = detailSql();

        String filter = normalizeParts(StoreManageMapper.DETAIL_NON_EMPTY_WHERE).get(0);
        assertThat(filter).isEqualTo("where (t.demandqty != 0 or t.saleqty != 0 or t.returnqty != 0)");
        // 必须落在合并子查询之外：MP 自动 count 是 SELECT COUNT(*) FROM (聚合) t WHERE …，
        // 挪进子查询里 count 就数不到这个条件，「已到底」判断会跟着错
        assertThat(detail.substring(detail.lastIndexOf(") t"))).contains(filter);
    }

    @Test
    @DisplayName("逐产品环比基数：与明细共用同一份 FROM/WHERE，只多产品白名单，且不带空行过滤")
    void prevMonthSumsShareConditionsAndScopeToProducts() throws Exception {
        String prev = prevSumsSql();

        for (String fragment : List.of(
            StoreManageMapper.DEMAND_FROM, StoreManageMapper.DEMAND_WHERE,
            StoreManageMapper.SALE_FROM, StoreManageMapper.SALE_WHERE,
            StoreManageMapper.RETURN_FROM, StoreManageMapper.RETURN_WHERE)) {
            for (String part : normalizeParts(fragment)) {
                assertThat(prev).as("上月聚合含片段 [%s]", part).contains(part);
            }
        }
        // 三支各自被产品白名单收窄（三支写法一致 → 出现 3 次）
        String idFilter = normalizeParts(StoreManageMapper.DETAIL_PRODUCT_ID_FILTER).get(0);
        assertThat(prev.split(Pattern.quote(idFilter), -1)).as("三支都带产品白名单").hasSize(4);
        assertThat(prev.split("union all", -1)).as("UNION ALL 三个分支").hasSize(3);
        assertThat(prev).contains("group by g.productid");
        // 上月为 0 是合法基数（hasBase=false 渲染黑色 0.00%），不能被空行过滤掉
        assertThat(prev).doesNotContain(normalizeParts(StoreManageMapper.DETAIL_NON_EMPTY_WHERE).get(0));
    }

    /**
     * 每条 {@code @Select} 的 {@code <script>} 都能被 MyBatis 当 XML 解析并渲染出 SQL。
     *
     * <p>MyBatis 把注解里的 SQL 当 XML 读：裸 {@code <} / {@code >} / {@code <>} 会让
     * {@code XMLLanguageDriver} 在启动期抛 SAXParseException，表现为「应用起不来」。
     * 上面那些 contains 断言只看字符串，看不出这个——所以这里真跑一遍解析 + 绑定。</p>
     */
    @Test
    @DisplayName("全部 @Select 能被 XMLLanguageDriver 解析并绑定（裸 < / > 会在这里先炸，而不是启动时）")
    void allSelectAnnotationsParseAndBind() {
        Configuration configuration = new Configuration();
        LanguageDriver driver = new XMLLanguageDriver();

        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", "1001");
        params.put("storeId", null);
        params.put("monthStart", LocalDate.of(2026, 9, 1));
        params.put("nextStart", LocalDate.of(2026, 10, 1));
        params.put("belongTypes", List.of("pork", "white_bar"));
        params.put("productIds", List.of(1L, 2L));

        for (Method method : StoreManageMapper.class.getDeclaredMethods()) {
            Select select = method.getAnnotation(Select.class);
            if (select == null) {
                continue;
            }
            String script = String.join(" ", select.value());
            SqlSource sqlSource = driver.createSqlSource(configuration, script, Object.class);
            BoundSql boundSql = sqlSource.getBoundSql(params);
            assertThat(boundSql.getSql()).as("%s 渲染出的 SQL", method.getName())
                .isNotBlank()
                .doesNotContain("foreach");
        }
    }

    private static String detailSql() throws Exception {
        return normalizedSql("selectProductDetailPage",
            IPage.class, String.class, Long.class, LocalDate.class, LocalDate.class, List.class);
    }

    private static String prevSumsSql() throws Exception {
        return normalizedSql("selectProductMonthSums",
            String.class, Long.class, LocalDate.class, LocalDate.class, List.class, List.class);
    }

    @Test
    @DisplayName("全部聚合显式带 tenant_id + del_flag（不走 BaseMapperPlus 自动注入）")
    void allQueriesFilterTenantAndDelFlag() throws Exception {
        for (String m : List.of("countArrivedProducts", "sumDemandQty", "sumSaleQty", "sumReturnQty")) {
            String sql = normalizedSql(m);
            assertThat(sql).as(m + " 带租户").contains("tenant_id = #{tenantid}");
            assertThat(sql).as(m + " 产品表软删过滤").contains("p.del_flag = '0'");
            assertThat(sql).as(m + " storeId 可空").contains("#{storeid} is null or");
        }
    }

}
