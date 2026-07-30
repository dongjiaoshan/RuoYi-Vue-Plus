package org.dromara.djs.warehouse.loss.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("local")
@Tag("dev")
@DisplayName("LossOverviewMapper SQL 口径契约")
class LossOverviewMapperSqlContractTest {

    private static String normalizedSql(String methodName, Class<?>... paramTypes) throws Exception {
        Method method = LossOverviewMapper.class.getMethod(methodName, paramTypes);
        Select select = method.getAnnotation(Select.class);
        return String.join(" ", select.value()).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("admin row121：每日损耗品种数 = 当日明细按产品编码去重后的数量")
    void dailyProductCountUsesDistinctProductCode() throws Exception {
        String sql = normalizedSql("selectDailyOverview", String.class, java.util.Date.class, java.util.Date.class);

        assertThat(sql)
            .contains("count(distinct product_code) as productcount")
            .doesNotContain("count(*)");
    }

    @Test
    @DisplayName("admin row122：燎毛损耗明细单位固定 kg")
    void detailForcesKgUnitForBurnLoss() throws Exception {
        String sql = normalizedSql("selectDailyDetail", String.class, String.class, String.class, String.class);

        assertThat(sql)
            .contains("case when lf.loss_type = 'burn_loss' then 'kg' else lf.product_unit end as productunit");
    }
}
