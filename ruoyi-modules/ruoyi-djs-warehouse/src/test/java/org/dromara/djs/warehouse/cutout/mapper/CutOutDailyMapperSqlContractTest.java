package org.dromara.djs.warehouse.cutout.mapper;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.dromara.djs.warehouse.cutout.domain.query.CutOutDailyQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CutOutDailyMapper} SQL 口径契约（V7-R6）。
 *
 * <p>锁统计范围的判别条件（改坏了不会报错，只会悄悄多算 / 少算）：</p>
 * <ol>
 *   <li>仓库出库只认 cut_room_out 出库流水，且 white_bar_no 为空（排除猪只入库管理的燎毛副产直接出库）。</li>
 *   <li>门店需求只认带 white_bar_no 或 white_bar_id 的产出记录（普通肉品打包都不写），必须有门店、排除后台出库。</li>
 *   <li>聚合与明细共用同一份 WHERE（明细求和 = 汇总行）。</li>
 * </ol>
 */
@Tag("local")
@Tag("dev")
@DisplayName("CutOutDailyMapper SQL 口径契约（V7-R6）")
class CutOutDailyMapperSqlContractTest {

    private static String norm(String sql) {
        return sql.replace("&lt;", "<").replace("&gt;", ">").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("仓库出库：cut_room_out + OT + white_bar_no 为空 + tenant/del_flag")
    void warehouseScope() {
        String w = norm(CutOutDailyMapper.WAREHOUSE_WHERE);
        assertThat(w).contains("f.flow_type = 'cut_room_out'");
        assertThat(w).contains("f.inout_type = 'ot'");
        assertThat(w).contains("(f.white_bar_no is null or f.white_bar_no = '')");
        assertThat(w).contains("f.del_flag = '0'").contains("f.tenant_id = #{tenantid}");
    }

    @Test
    @DisplayName("门店需求：white_bar_no 或 white_bar_id 非空 + 有门店 + 非后台出库 + tenant/del_flag")
    void storeScope() {
        String s = norm(CutOutDailyMapper.STORE_WHERE);
        assertThat(s).contains("((p.white_bar_no is not null and p.white_bar_no <> '') or p.white_bar_id is not null)");
        assertThat(s).contains("p.store_id is not null");
        assertThat(s).contains("p.deliver_dest <> 'warehouse_out'");
        assertThat(s).contains("p.del_flag = '0'").contains("p.tenant_id = #{tenantid}");
    }

    @Test
    @DisplayName("聚合与明细共用 WHERE；量用 change_quantity / product_weight")
    void sharedWhere() {
        assertThat(CutOutDailyMapper.DAILY_SQL).contains(CutOutDailyMapper.WAREHOUSE_WHERE).contains(CutOutDailyMapper.STORE_WHERE);
        assertThat(CutOutDailyMapper.WAREHOUSE_DETAIL_SQL).contains(CutOutDailyMapper.WAREHOUSE_WHERE);
        assertThat(CutOutDailyMapper.STORE_DETAIL_SQL).contains(CutOutDailyMapper.STORE_WHERE);
        String daily = norm(CutOutDailyMapper.DAILY_SQL);
        assertThat(daily).contains("sum(f.change_quantity)").contains("sum(p.product_weight)");
        assertThat(daily).doesNotContain("change_num");
        assertThat(daily).contains("order by g.statdate desc");
    }

    /**
     * 用 MyBatis 自己的 XML 驱动把 &lt;script&gt; 渲染一遍：OGNL / 标签写错在这里就炸，不用等起后端。
     */
    private static String render(boolean withWarehouse, boolean withStore, CutOutDailyQuery query) {
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", "1001");
        params.put("query", query);
        params.put("withWarehouse", withWarehouse);
        params.put("withStore", withStore);
        BoundSql bound = new XMLLanguageDriver()
            .createSqlSource(new Configuration(), CutOutDailyMapper.DAILY_SQL, Map.class)
            .getBoundSql(params);
        return bound.getSql().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("列表 SQL 渲染：两分支都开时 UNION ALL；只开一个分支时没有 UNION、也没有另一张表")
    void dailySqlRendersBranches() {
        CutOutDailyQuery q = new CutOutDailyQuery();
        q.setBeginDate(new Date());
        q.setEndDate(new Date());
        String both = render(true, true, q);
        assertThat(both).contains("union all").contains("t_warehouse_stock_flow").contains("t_warehouse_product_production");

        q.setOutDest("kitchen");
        String warehouseOnly = render(true, false, q);
        assertThat(warehouseOnly).doesNotContain("union all").doesNotContain("t_warehouse_product_production")
            .contains("f.stock_out_dest = ?");

        CutOutDailyQuery byStore = new CutOutDailyQuery();
        byStore.setStoreId(1L);
        String storeOnly = render(false, true, byStore);
        assertThat(storeOnly).doesNotContain("union all").doesNotContain("t_warehouse_stock_flow")
            .contains("p.store_id = ?");
    }
}
