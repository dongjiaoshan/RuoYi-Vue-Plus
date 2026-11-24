package org.dromara.djs.warehouse.inout.mapper;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.Collection;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("local") @Tag("dev")
class InoutMapperSqlTest {
    TenantLineInnerInterceptor tenant = new TenantLineInnerInterceptor(new TenantLineHandler() {
        public Expression getTenantId() { return new StringValue("1001"); }
    });
    @Test void workbenchSelectsSurviveTenantRewriteIncludingNestedAggregations() {
        for (var method : InoutWorkbenchMapper.class.getMethods()) {
            Select select = method.getAnnotation(Select.class);
            if (select == null) { continue; }
            String rewritten = tenant.parserSingle(String.join(" ",select.value()).replaceAll("#\\{[^}]+}","?"),null);
            assertThat(rewritten).contains("tenant_id = '1001'");
            assertThat(rewritten).doesNotContain("tenant_id = null");
        }
    }
    @Test void burnedHistoryIncludesConsumedSourcesOnceWithoutLockingCrossPigScan() throws Exception {
        String sql=String.join(" ", StockFlowMapper.class.getMethod("selectBurnInbounds",Collection.class).getAnnotation(Select.class).value());
        sql=sql.replace("<script>","").replace("</script>","")
            .replace("<foreach collection='barIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>","(1,2)");
        String rewritten=tenant.parserSingle(sql,null);
        assertThat(rewritten).doesNotContain("FOR UPDATE").contains("MAX(white_bar_id)")
            .contains("GROUP BY tenant_id, white_bar_no").doesNotContain("i.del_flag");
    }
    @Test void cutAggregatesDoNotLockFlowRangesButReceiptStillLocks() throws Exception {
        for (String method : new String[]{"sumCutOutByWhiteBarId", "sumCutOutByWhiteBarNo"}) {
            Class<?> argument = method.endsWith("No") ? String.class : Long.class;
            String sql = String.join(" ", StockFlowMapper.class.getMethod(method, argument).getAnnotation(Select.class).value());
            assertThat(sql).contains("SUM(change_quantity)", "cut_out_in").doesNotContain("FOR UPDATE");
        }
        String receipt = String.join(" ", StockFlowMapper.class.getMethod("selectRequestReceipt", String.class).getAnnotation(Select.class).value());
        assertThat(receipt).contains("FOR UPDATE");
    }
    @Test void cutBasketIncrementUsesPrimaryKeyAndPreservesTenantAndShapeGuards() throws Exception {
        String sql = String.join(" ", org.dromara.djs.warehouse.stock.mapper.LocationStockMapper.class
            .getMethod("addCutBasketById", Long.class, Long.class, Long.class, String.class,
                java.math.BigDecimal.class, Long.class).getAnnotation(org.apache.ibatis.annotations.Update.class).value());
        assertThat(sql).contains("WHERE id = #{id}", "location_id = #{locationId}", "product_id = #{productId}",
            "ear_no = #{earNo}", "white_bar_no IS NULL", "is_end = 0", "del_flag = '0'");
        assertThat(tenant.parserSingle(sql.replaceAll("#\\{[^}]+}", "?"), null))
            .contains("tenant_id = '1001'");
    }
    @Test void cutDemandCteExposesTenantAndUsesOnlyTodayEligibleProduction() throws Exception {
        String sql=String.join(" ",InoutWorkbenchMapper.class.getMethod("selectCutStoreDemands",Long.class,java.time.LocalDate.class).getAnnotation(Select.class).value());
        assertThat(sql).contains("SELECT p.tenant_id,", "dm.demand_date>=#{today}", "p.product_material=#{materialProductId}",
            "p.is_material_sold=1", "p.product_attr=1", "p.product_status=0", "p.is_delivery=1");
        assertThat(sql).doesNotContain("dm.demand_date=#{today}", "'COMPLETED'");
    }
}
