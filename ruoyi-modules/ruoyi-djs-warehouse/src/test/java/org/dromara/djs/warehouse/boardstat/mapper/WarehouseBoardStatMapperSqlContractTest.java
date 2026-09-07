package org.dromara.djs.warehouse.boardstat.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
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
 *   <li><b>卡片与明细共用同一份筛选条件</b>：入库 / 生产两对（聚合 SQL、明细分页 SQL）逐条比对
 *       {@code *_WHERE} 常量的每一行，谁在自己那一侧偷偷加减条件，明细就与卡片对不上数，
 *       这里当场红。</li>
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

    @Test
    @DisplayName("入库：卡片聚合与入库明细共用同一份 FROM / WHERE，逐条对齐")
    void inboundCardAndDetailShareConditions() throws Exception {
        String card = selectSql("selectInboundByCategoryUnit",
            String.class, List.class, List.class, LocalDate.class, LocalDate.class);
        String detail = selectSql("selectInboundDetailPage",
            IPage.class, String.class, List.class, List.class, LocalDate.class, LocalDate.class);

        for (String fragment : normalizeLines(WarehouseBoardStatMapper.INBOUND_FROM)) {
            assertThat(card).as("卡片 SQL 含 FROM 片段 [%s]", fragment).contains(fragment);
            assertThat(detail).as("明细 SQL 含 FROM 片段 [%s]", fragment).contains(fragment);
        }
        for (String fragment : normalizeLines(WarehouseBoardStatMapper.INBOUND_WHERE)) {
            assertThat(card).as("卡片 SQL 含 WHERE 片段 [%s]", fragment).contains(fragment);
            assertThat(detail).as("明细 SQL 含 WHERE 片段 [%s]", fragment).contains(fragment);
        }
        // 明细逐行的量就是聚合里被 SUM 的那一列 → 按单位 Σ 必然等于卡片数字
        assertThat(detail).contains("f.change_quantity").doesNotContain("f.change_num");
        assertThat(detail).contains("order by f.flow_date desc");
    }

    @Test
    @DisplayName("生产：卡片聚合与生产明细共用同一份 FROM / WHERE，且行值照抄 CASE 两个分支")
    void produceCardAndDetailShareConditions() throws Exception {
        String card = selectSql("selectProduceByCategoryUnit",
            String.class, List.class, LocalDate.class, LocalDate.class);
        String detail = selectSql("selectProduceDetailPage",
            IPage.class, String.class, List.class, LocalDate.class, LocalDate.class);

        for (String fragment : normalizeLines(WarehouseBoardStatMapper.PRODUCE_FROM)) {
            assertThat(card).as("卡片 SQL 含 FROM 片段 [%s]", fragment).contains(fragment);
            assertThat(detail).as("明细 SQL 含 FROM 片段 [%s]", fragment).contains(fragment);
        }
        for (String fragment : normalizeLines(WarehouseBoardStatMapper.PRODUCE_WHERE)) {
            assertThat(card).as("卡片 SQL 含 WHERE 片段 [%s]", fragment).contains(fragment);
            assertThat(detail).as("明细 SQL 含 WHERE 片段 [%s]", fragment).contains(fragment);
        }
        // 聚合：kg 取 SUM(product_weight)，其余取 COUNT(*)；明细：kg 取本行 product_weight，其余取 1
        assertThat(card).contains("sum(pp.product_weight)").contains("else count(*) end");
        assertThat(detail).contains("coalesce(pp.product_weight, 0)").contains("else 1 end");
        assertThat(detail).contains("order by pp.produce_date desc");
    }

    /**
     * 把 SQL 片段常量拆成可断言的单行（与 {@link #selectSql} 同一套归一：压空白 + 转小写）。
     *
     * @param fragment 片段常量
     * @return 归一后的非空行
     */
    private static List<String> normalizeLines(String fragment) {
        return fragment.lines()
            .map(line -> line.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT))
            .filter(line -> !line.isEmpty())
            .toList();
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
