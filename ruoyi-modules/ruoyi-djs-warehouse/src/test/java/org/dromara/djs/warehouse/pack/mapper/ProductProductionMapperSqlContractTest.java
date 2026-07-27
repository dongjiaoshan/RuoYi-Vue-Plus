package org.dromara.djs.warehouse.pack.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("local")
@Tag("dev")
@DisplayName("产品生产需求量与满足率 SQL 口径契约")
class ProductProductionMapperSqlContractTest {

    @Test
    @DisplayName("需求先按租户产品日期汇总，再以 MAX 接入生产分组防止多生产行倍增")
    void demandQuantityIsAggregatedBeforeProductionJoin() throws Exception {
        String sql = productionGroupSql();

        assertThat(sql)
            .contains("sum(demand_quantity) as demand_qty")
            .contains("group by tenant_id, product_id, date(demand_date)")
            .contains("coalesce(max(dm.demand_qty), 0) as demandqty")
            .contains("dm.tenant_id = pp.tenant_id")
            .contains("dm.product_id = pp.product_id")
            .contains("dm.demand_day = date(pp.produce_date)")
            .contains("demand_status not in ('cancelled', 'deleted')")
            .contains("where del_flag = '0'");
    }

    @Test
    @DisplayName("满足率严格为需求量除以生产记录数乘100并保留两位")
    void fulfillmentRateUsesAcceptedFormula() throws Exception {
        String sql = productionGroupSql();

        assertThat(sql)
            .contains("round(coalesce(max(dm.demand_qty), 0) / count(*) * 100, 2)")
            .contains("count(*) as produceqty")
            .contains("case when count(*) = 0 then 0");
    }

    private static String productionGroupSql() throws Exception {
        Method method = ProductProductionMapper.class.getMethod(
            "selectProductionGroupList",
            String.class,
            String.class,
            String.class,
            List.class,
            Integer.class,
            Date.class,
            Date.class,
            Integer.class
        );
        Select select = method.getAnnotation(Select.class);
        assertThat(select).isNotNull();
        return String.join(" ", select.value())
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }
}
