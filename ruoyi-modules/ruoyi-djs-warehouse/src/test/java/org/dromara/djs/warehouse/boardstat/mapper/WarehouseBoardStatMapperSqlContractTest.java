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
 * mp 仓库统计三指标 SQL 口径契约测试（V6-R178 / R193）。
 *
 * <p>三条口径极易被后来者「顺手统一」掉，故直接锁在 SQL 上：</p>
 * <ol>
 *   <li>「只统计原材料产品」只约束<b>入库量</b>（product_attr = 2），另两个指标不带这个条件；</li>
 *   <li>生产量按产品自身单位取值：kg / 公斤 取重量合计，其余单位取记录条数；</li>
 *   <li>原材料消耗量按 {@code material_id} 关联<b>原材料</b>档案归组，不是按成品档案。</li>
 *   <li><b>礼盒生产的原材料消耗不统计</b>（甲方 2026-09-07）：排除条件只挂在原材料消耗那一条 SQL 上，
 *       入库 / 生产 / 两个明细四条都不许带 —— 顺手加错地方就会把礼盒自己的产量也统计没了；</li>
 *   <li><b>卡片与明细共用同一份筛选条件</b>：入库 / 生产两对（品类聚合 SQL、产品聚合明细 SQL）
 *       逐条比对 {@code *_WHERE} 常量的每一行，谁在自己那一侧偷偷加减条件，明细就与卡片对不上数，
 *       这里当场红；明细的聚合表达式也必须与卡片同一个（SUM / CASE 两分支），
 *       否则「按单位 Σ 明细 = 卡片」这条构造性保证就断了。</li>
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
    @DisplayName("原材料消耗排除礼盒生产（甲方 2026-09-07：礼盒生产的不进行统计）")
    void materialConsumeExcludesGiftBoxProduce() throws Exception {
        String sql = selectSql("selectMaterialConsumeByCategoryUnit",
            String.class, List.class, LocalDate.class, LocalDate.class);

        for (String fragment : normalizeLines(WarehouseBoardStatMapper.EXCLUDE_GIFT_PRODUCE)) {
            assertThat(sql).as("原材料消耗 SQL 含礼盒排除片段 [%s]", fragment).contains(fragment);
        }
        // 判礼盒只认 belong_type：djs_product_type 的 3=礼盒 已废弃（字典项删除 + 存量迁回 1），
        // 谁改回 product_type = 3 这里当场红
        assertThat(sql)
            .contains("po.belong_type = 'gift_box'")
            .doesNotContain("product_type");
        // 半连接不改行数：排除条件不许写成再 JOIN 一张产品档案
        assertThat(sql).doesNotContain("join t_warehouse_product_info po");
    }

    @Test
    @DisplayName("礼盒排除只挂原材料消耗一条：入库量 / 生产量 / 两个明细都不许带")
    void giftBoxExclusionStaysOnMaterialConsumeOnly() throws Exception {
        List<String> untouched = List.of(
            selectSql("selectInboundByCategoryUnit",
                String.class, List.class, List.class, LocalDate.class, LocalDate.class),
            selectSql("selectInboundDetailByProduct",
                String.class, List.class, List.class, LocalDate.class, LocalDate.class),
            selectSql("selectProduceByCategoryUnit",
                String.class, List.class, LocalDate.class, LocalDate.class),
            selectSql("selectProduceDetailByProduct",
                String.class, List.class, LocalDate.class, LocalDate.class));

        for (String sql : untouched) {
            assertThat(sql).doesNotContain("gift_box").doesNotContain("not exists");
        }
    }

    @Test
    @DisplayName("入库：卡片聚合与入库明细共用同一份 FROM / WHERE，明细只把 GROUP BY 细到产品")
    void inboundCardAndDetailShareConditions() throws Exception {
        String card = selectSql("selectInboundByCategoryUnit",
            String.class, List.class, List.class, LocalDate.class, LocalDate.class);
        String detail = selectSql("selectInboundDetailByProduct",
            String.class, List.class, List.class, LocalDate.class, LocalDate.class);

        for (String fragment : normalizeLines(WarehouseBoardStatMapper.INBOUND_FROM)) {
            assertThat(card).as("卡片 SQL 含 FROM 片段 [%s]", fragment).contains(fragment);
            assertThat(detail).as("明细 SQL 含 FROM 片段 [%s]", fragment).contains(fragment);
        }
        for (String fragment : normalizeLines(WarehouseBoardStatMapper.INBOUND_WHERE)) {
            assertThat(card).as("卡片 SQL 含 WHERE 片段 [%s]", fragment).contains(fragment);
            assertThat(detail).as("明细 SQL 含 WHERE 片段 [%s]", fragment).contains(fragment);
        }
        // 明细的聚合表达式与卡片是同一个 SUM，只是分组细到「产品 × 单位」→ 按单位 Σ 必然等于卡片数字
        assertThat(detail)
            .contains("coalesce(sum(f.change_quantity), 0) as qty")
            .contains("group by pi.id, coalesce(pi.product_unit, '')")
            .doesNotContain("f.change_num");
        // 产品名 / 规格走 MAX 而不进 GROUP BY，否则规格改过的历史数据会把一个产品劈成两行
        assertThat(detail).contains("max(pi.product_name)").contains("cast(pi.id as char) as productid");
        assertThat(detail).contains("order by qty desc");
    }

    @Test
    @DisplayName("生产：卡片聚合与生产明细共用同一份 FROM / WHERE，且明细照抄卡片的 CASE 两个分支")
    void produceCardAndDetailShareConditions() throws Exception {
        String card = selectSql("selectProduceByCategoryUnit",
            String.class, List.class, LocalDate.class, LocalDate.class);
        String detail = selectSql("selectProduceDetailByProduct",
            String.class, List.class, LocalDate.class, LocalDate.class);

        for (String fragment : normalizeLines(WarehouseBoardStatMapper.PRODUCE_FROM)) {
            assertThat(card).as("卡片 SQL 含 FROM 片段 [%s]", fragment).contains(fragment);
            assertThat(detail).as("明细 SQL 含 FROM 片段 [%s]", fragment).contains(fragment);
        }
        for (String fragment : normalizeLines(WarehouseBoardStatMapper.PRODUCE_WHERE)) {
            assertThat(card).as("卡片 SQL 含 WHERE 片段 [%s]", fragment).contains(fragment);
            assertThat(detail).as("明细 SQL 含 WHERE 片段 [%s]", fragment).contains(fragment);
        }
        // 两边同一条 CASE：kg/公斤 取 SUM(product_weight)，其余单位取 COUNT(*)
        for (String sql : List.of(card, detail)) {
            assertThat(sql).contains("in ('kg', '公斤')")
                .contains("coalesce(sum(pp.product_weight), 0)")
                .contains("else count(*) end");
        }
        assertThat(detail)
            .contains("group by pi.id, coalesce(pi.product_unit, pp.product_unit, '')")
            .contains("order by qty desc")
            // 弹窗只有四列，明细不再联原材料档案
            .doesNotContain("pp.material_id");
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
