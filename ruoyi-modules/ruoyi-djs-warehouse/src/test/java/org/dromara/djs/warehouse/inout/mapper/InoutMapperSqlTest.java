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
            String rewritten = tenant.parserSingle(String.join(" ",select.value()).replace("#{cutRecordId}","20"),null);
            assertThat(rewritten).contains("tenant_id = '1001'");
            assertThat(rewritten).doesNotContain("tenant_id = null");
        }
    }
    @Test void burnedHistoryIncludesConsumedSourcesOnceAndRetainsCurrentRead() throws Exception {
        String sql=String.join(" ", StockFlowMapper.class.getMethod("selectBurnInbounds",Collection.class).getAnnotation(Select.class).value());
        sql=sql.replace("<script>","").replace("</script>","")
            .replace("<foreach collection='barIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>","(1,2)");
        String rewritten=tenant.parserSingle(sql,null);
        assertThat(rewritten).endsWith("FOR UPDATE").contains("MAX(white_bar_id)")
            .contains("GROUP BY tenant_id, white_bar_no").doesNotContain("i.del_flag");
    }
}
