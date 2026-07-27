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

    @Test
    @DisplayName("admin row105：每日产品数量严格等于当日有效损耗明细行数")
    void dailyProductCountUsesDetailRowCount() throws Exception {
        Method method = LossOverviewMapper.class.getMethod(
            "selectDailyOverview", String.class, java.util.Date.class, java.util.Date.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);

        assertThat(sql)
            .contains("count(*) as productcount")
            .doesNotContain("count(distinct");
    }
}
