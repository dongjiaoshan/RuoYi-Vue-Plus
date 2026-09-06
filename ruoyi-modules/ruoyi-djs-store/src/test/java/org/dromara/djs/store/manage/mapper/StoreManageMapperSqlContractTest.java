package org.dromara.djs.store.manage.mapper;

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
        Method method = StoreManageMapper.class.getMethod(methodName,
            String.class, Long.class, LocalDate.class, LocalDate.class, List.class);
        Select select = method.getAnnotation(Select.class);
        return String.join(" ", select.value()).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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
