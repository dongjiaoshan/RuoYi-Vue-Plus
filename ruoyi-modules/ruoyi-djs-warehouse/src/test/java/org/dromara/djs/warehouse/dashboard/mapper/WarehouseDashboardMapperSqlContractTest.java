package org.dromara.djs.warehouse.dashboard.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仓库总览核心指标 SQL 口径契约测试。
 *
 * <p>这两项曾因复制了旧聚合口径而漂移：送宰猪只误按燎毛称重时间统计，
 * 分割产品总重误按后续打包生产记录统计。这里直接锁定 Mapper 的权威数据源。</p>
 */
@Tag("local")
@Tag("dev")
@DisplayName("WarehouseDashboardMapper SQL 口径契约")
class WarehouseDashboardMapperSqlContractTest {

    @Test
    @DisplayName("送宰猪只按当天出栏选择 marketing_time 统计，并排除外购")
    void slaughterPigCountUsesMarketingTime() throws Exception {
        String sql = selectSql("countTodaySlaughterPigs");

        assertThat(sql)
            .contains("date(marketing_time) = curdate()")
            .contains("buy_date is null")
            .doesNotContain("date(in_time)")
            .doesNotContain("arrive_weight is not null");
    }

    @Test
    @DisplayName("白条分割产品总重按当天 cut_out_in 入库流水统计")
    void cutProductWeightUsesCutOutStockFlow() throws Exception {
        String sql = selectSql("sumTodayCutProductWeight");

        assertThat(sql)
            .contains("from t_warehouse_stock_flow")
            .contains("flow_type = 'cut_out_in'")
            .contains("date(flow_date) = curdate()")
            .contains("sum(change_quantity)")
            .doesNotContain("t_warehouse_product_production");
    }

    private static String selectSql(String methodName) throws Exception {
        Method method = WarehouseDashboardMapper.class.getMethod(methodName, String.class);
        Select select = method.getAnnotation(Select.class);
        assertThat(select).as("@Select on %s", methodName).isNotNull();
        return String.join(" ", select.value())
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }
}
