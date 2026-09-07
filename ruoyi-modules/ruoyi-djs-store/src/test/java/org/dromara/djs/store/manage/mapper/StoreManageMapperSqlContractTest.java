package org.dromara.djs.store.manage.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

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

    private static String detailSql() throws Exception {
        return normalizedSql("selectProductDetailPage",
            IPage.class, String.class, Long.class, LocalDate.class, LocalDate.class, List.class);
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
