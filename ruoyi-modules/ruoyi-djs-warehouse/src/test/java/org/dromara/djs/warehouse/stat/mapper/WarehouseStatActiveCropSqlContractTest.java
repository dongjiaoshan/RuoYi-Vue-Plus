package org.dromara.djs.warehouse.stat.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code selectActiveCropIds} 的 0 值过滤口径契约。
 *
 * <p>V6 r28/r29/r40/r41 之后「0 kg 收口」是合法提交：工人已经称完 / 处理完，只是当天没有产出。
 * 这类记录不能把作物拉进当日日报 —— 否则 {@code t_warehouse_cropp_record} 会多出一整行全 0 的数据。
 * 同链路其它取数点（PICK_DETAIL_SQL / VEG_HANDLE_RECORD_SQL / 绩效 / 看板品种数）都带
 * {@code record_weight > 0}，这里曾是唯一漏网的一处。</p>
 *
 * <p>只钉 handle_record 这一支：veg_receive（月台接收）与 feed_log（饲喂台账）不产生 0 值收口记录，
 * 给它们加过滤反而会把合法的 0 量业务行筛掉。</p>
 */
@Tag("local")
@Tag("dev")
@DisplayName("WarehouseStatAggregateMapper.selectActiveCropIds 0 值过滤口径")
class WarehouseStatActiveCropSqlContractTest {

    private static String script() throws Exception {
        Method method = WarehouseStatAggregateMapper.class
            .getMethod("selectActiveCropIds", String.class, String.class);
        return String.join(" ", method.getAnnotation(Select.class).value()).trim();
    }

    @Test
    @DisplayName("handle_record 支必须过滤 record_weight > 0（0 kg 收口不进当日作物日报）")
    void handleRecordBranchFiltersZeroWeight() throws Exception {
        String sql = script();
        int handleIdx = sql.indexOf("t_warehouse_handle_record");
        assertThat(handleIdx).as("SQL 里应有 handle_record 支").isGreaterThan(-1);
        // 取 handle_record 支到下一个 UNION 之间的片段，确保过滤条件挂在这一支上而不是别支
        int unionIdx = sql.indexOf("UNION", handleIdx);
        String branch = unionIdx > 0 ? sql.substring(handleIdx, unionIdx) : sql.substring(handleIdx);
        assertThat(branch.replace("&gt;", ">"))
            .as("handle_record 支缺 record_weight > 0，0 kg 收口会让该作物落一行全 0 日报")
            .contains("record_weight > 0");
    }

    @Test
    @DisplayName("<script> 动态 SQL 可被 MyBatis 解析（写坏了是启动期炸，不是接口报错）")
    void scriptParses() throws Exception {
        Configuration cfg = new Configuration();
        SqlSource src = new XMLLanguageDriver().createSqlSource(cfg, script(), Object.class);
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", "1001");
        params.put("statDate", "2026-08-06");
        BoundSql bound = src.getBoundSql(params);
        assertThat(bound.getSql()).contains("t_warehouse_handle_record", "t_warehouse_veg_receive", "t_warehouse_feed_log");
    }
}
