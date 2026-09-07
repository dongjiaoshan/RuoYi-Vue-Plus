package org.dromara.djs.warehouse.dashboard.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WarehouseProductionDashboardMapper} 取数口径契约（V6-R172）。
 *
 * <p>年度屠宰率 / 白条出品率在 service 侧按「Σcohort 基数列 相除」算（与日表 / 月表同源）。
 * 那四个基数列是靠 {@code SELECT *} 整行取回来的——谁把它收窄成显式列清单又漏了基数列，
 * 年度率会静默变成 null / 0（entity 字段拿不到值），没有任何编译期报错。本类钉住这一点。</p>
 *
 * @author djs
 * @since V6-R172
 */
@Tag("local")
@Tag("dev")
@DisplayName("WarehouseProductionDashboardMapper 取数口径契约（V6-R172）")
class WarehouseProductionDashboardMapperSqlContractTest {

    @Test
    @DisplayName("日表整行取回：年度率要用的 4 个 cohort 基数列必须在结果里")
    void indicatorRangeSelectsWholeRowSoCohortBasesAreAvailable() throws Exception {
        String sql = selectSql("selectIndicatorRecordsInRange",
            String.class, LocalDate.class, LocalDate.class);

        assertThat(sql).as("整行取回，年度 KPI 才拿得到 cohort 基数列")
            .contains("select * from t_warehouse_indicator_record");
        assertThat(sql).contains("stat_date between #{from} and #{to}");
        assertThat(sql).as("dashboard 聚合不保证租户拦截器注入，必须显式带 tenant_id")
            .contains("tenant_id = #{tenantid}");
    }

    @Test
    @DisplayName("月度趋势整行取回月表（折线直读月表已算好的比率）")
    void monthlyByYearSelectsWholeRow() throws Exception {
        String sql = selectSql("selectMonthlyRecordsByYear", String.class, String.class);

        assertThat(sql).contains("select * from t_warehouse_monthly_record");
        assertThat(sql).contains("stat_month like #{yearprefix}");
        assertThat(sql).contains("tenant_id = #{tenantid}");
    }

    private static String selectSql(String methodName, Class<?>... paramTypes) throws Exception {
        Method method = WarehouseProductionDashboardMapper.class.getMethod(methodName, paramTypes);
        Select select = method.getAnnotation(Select.class);
        assertThat(select).as("@Select on %s", methodName).isNotNull();
        return String.join(" ", select.value())
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }
}
