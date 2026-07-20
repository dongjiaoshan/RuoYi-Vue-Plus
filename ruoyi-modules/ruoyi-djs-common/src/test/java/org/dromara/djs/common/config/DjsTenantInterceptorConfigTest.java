package org.dromara.djs.common.config;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DjsTenantInterceptorConfig} 单测：focus 在白名单 + TenantLineHandler 行为。
 *
 * <p>不启动 Spring 上下文（避免引 ruoyi-admin / db 依赖），直接 new 内部 handler 验证。
 *
 * @author djs
 * @since SYS-INFRA-007
 */
@Tag("local")
@Tag("dev")
class DjsTenantInterceptorConfigTest {

    private final DjsTenantInterceptorConfig.DjsTenantLineHandler handler =
        new DjsTenantInterceptorConfig.DjsTenantLineHandler();

    // ---------------- 白名单 ----------------

    @Test
    @DisplayName("白名单包含 ruoyi 自带共享表 sys_user / sys_dict_data 等")
    void ignoreTable_ruoyiSharedTables() {
        assertThat(handler.ignoreTable("sys_user")).isTrue();
        assertThat(handler.ignoreTable("sys_role")).isTrue();
        assertThat(handler.ignoreTable("sys_dict_type")).isTrue();
        assertThat(handler.ignoreTable("sys_dict_data")).isTrue();
        assertThat(handler.ignoreTable("sys_menu")).isTrue();
        assertThat(handler.ignoreTable("sys_oss_config")).isTrue();
    }

    @Test
    @DisplayName("白名单包含 djs 全局共享表 sys_farm + mp_subscribe_record")
    void ignoreTable_djsGlobalTables() {
        assertThat(handler.ignoreTable("sys_farm")).isTrue();
        assertThat(handler.ignoreTable("mp_subscribe_record")).isTrue();
    }

    @Test
    @DisplayName("djs 业务表（t_farm_* / t_plant_* / t_warehouse_* / t_store_*）不在白名单内 → 走 tenant 过滤")
    void ignoreTable_djsBusinessTables() {
        assertThat(handler.ignoreTable("t_farm_pig_info")).isFalse();
        assertThat(handler.ignoreTable("t_plant_crop_info")).isFalse();
        assertThat(handler.ignoreTable("t_warehouse_stock_flow")).isFalse();
        assertThat(handler.ignoreTable("t_store_member")).isFalse();
        assertThat(handler.ignoreTable("t_md_supplier")).isFalse();
    }

    @Test
    @DisplayName("大小写不敏感：SYS_USER / Sys_User 都识别为白名单")
    void ignoreTable_caseInsensitive() {
        assertThat(handler.ignoreTable("SYS_USER")).isTrue();
        assertThat(handler.ignoreTable("Sys_User")).isTrue();
        assertThat(handler.ignoreTable("sys_USER")).isTrue();
    }

    @Test
    @DisplayName("null 表名 → ignore（安全 fallback）")
    void ignoreTable_null() {
        assertThat(handler.ignoreTable(null)).isTrue();
    }

    // ---------------- getTenantId fallback ----------------

    @Test
    @DisplayName("无登录态时 fallback 到 V1 默认农场 '1001'")
    void getTenantId_fallbackToDefault() {
        // TenantHelper.getTenantId() 在无 Spring + 无 Sa-Token 上下文时返回 null
        Expression expr = handler.getTenantId();
        assertThat(expr).isInstanceOf(StringValue.class);
        assertThat(((StringValue) expr).getValue()).isEqualTo("1001");
    }

    // ---------------- 列名 ----------------

    @Test
    @DisplayName("tenant_id 列名固定为 tenant_id")
    void getTenantIdColumn() {
        assertThat(handler.getTenantIdColumn()).isEqualTo("tenant_id");
    }

    // ---------------- IGNORE_TABLES 完整性 ----------------

    @Test
    @DisplayName("IGNORE_TABLES 包含 doc/05 §4.3.1 规定的全部全局共享表")
    void ignoreTables_completeness() {
        // doc/05 §4.3.1 列了 18+ 表，这里抽样验证
        assertThat(DjsTenantInterceptorConfig.IGNORE_TABLES)
            .contains("sys_user", "sys_role", "sys_dept", "sys_post", "sys_menu",
                "sys_dict_type", "sys_dict_data", "sys_config", "sys_client",
                "sys_oss", "sys_oss_config", "sys_user_role", "sys_role_menu",
                "sys_role_dept", "sys_user_post", "gen_table", "gen_table_column",
                "sys_farm", "mp_subscribe_record");
    }
}
