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
    @DisplayName("生产量按产品单位分流：kg 取重量合计 / 计数单位取记录条数；满足率同量纲相除")
    void fulfillmentRateUsesAcceptedFormula() throws Exception {
        String sql = productionGroupSql();

        // kg 计量 → SUM(product_weight)；份 / 盒 / 枚 → COUNT(*)
        assertThat(sql)
            .contains("lower(trim(max(coalesce(pi.product_unit, pp.product_unit)))) in ('kg', '公斤')")
            .contains("then coalesce(sum(pp.product_weight), 0)")
            .contains("else count(*) end as produceqty");
        // 满足率 = 需求量 / 同单位生产量 * 100，分母 0 用 NULLIF 兜底
        assertThat(sql)
            .contains("round(coalesce(max(dm.demand_qty), 0) / nullif(")
            .contains("* 100, 2) as fulfillmentrate");
        // 旧口径（kg 产品拿记录条数当产量）不得复活
        assertThat(sql).doesNotContain("/ count(*) * 100");
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
