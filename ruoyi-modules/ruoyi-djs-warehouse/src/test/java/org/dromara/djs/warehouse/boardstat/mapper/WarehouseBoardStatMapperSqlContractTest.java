package org.dromara.djs.warehouse.boardstat.mapper;

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
 * mp 仓库统计三指标 SQL 口径契约测试（V6-R178）。
 *
 * <p>三条口径极易被后来者「顺手统一」掉，故直接锁在 SQL 上：</p>
 * <ol>
 *   <li>「只统计原材料产品」只约束<b>入库量</b>（product_attr = 2），另两个指标不带这个条件；</li>
 *   <li>生产量按产品自身单位取值：kg / 公斤 取重量合计，其余单位取记录条数；</li>
 *   <li>原材料消耗量按 {@code material_id} 关联<b>原材料</b>档案归组，不是按成品档案。</li>
 * </ol>
 */
@Tag("local")
@Tag("dev")
@DisplayName("WarehouseBoardStatMapper SQL 口径契约")
class WarehouseBoardStatMapperSqlContractTest {

    @Test
    @DisplayName("入库量只算原材料产品（product_attr = 2）且套用入库展示排除清单")
    void inboundCountsRawMaterialOnly() throws Exception {
        String sql = selectSql("selectInboundByCategoryUnit",
            String.class, List.class, List.class, LocalDate.class, LocalDate.class);

        assertThat(sql)
            .contains("from t_warehouse_stock_flow")
            .contains("f.inout_type = 'in'")
            .contains("pi.product_attr = 2")
            .contains("f.flow_type not in")
            .contains("sum(f.change_quantity)")
            .doesNotContain("f.change_num");
    }

    @Test
    @DisplayName("生产量按产品单位取值：kg/公斤 取重量合计，其余单位取记录条数")
    void produceQtyFollowsProductUnit() throws Exception {
        String sql = selectSql("selectProduceByCategoryUnit",
            String.class, List.class, LocalDate.class, LocalDate.class);

        assertThat(sql)
            .contains("from t_warehouse_product_production")
            .contains("in ('kg', '公斤')")
            .contains("sum(pp.product_weight)")
            .contains("else count(*) end")
            .doesNotContain("product_attr");
    }

    @Test
    @DisplayName("原材料消耗量按 material_id 关联原材料档案归组，不带原材料过滤")
    void materialConsumeJoinsMaterialProduct() throws Exception {
        String sql = selectSql("selectMaterialConsumeByCategoryUnit",
            String.class, List.class, LocalDate.class, LocalDate.class);

        assertThat(sql)
            .contains("sum(pp.material_consume)")
            .contains("pm.id = pp.material_id")
            .contains("pm.belong_type in")
            .contains("pp.material_id is not null")
            .doesNotContain("product_attr");
    }

    private static String selectSql(String methodName, Class<?>... paramTypes) throws Exception {
        Method method = WarehouseBoardStatMapper.class.getMethod(methodName, paramTypes);
        Select select = method.getAnnotation(Select.class);
        assertThat(select).as("@Select on %s", methodName).isNotNull();
        return String.join(" ", select.value())
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }
}
