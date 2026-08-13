package org.dromara.djs.common.store.context;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * {@link StoreLineHandler} 单元测试（STORE-PERM-001）—— 验证 3 条安全铁律。
 *
 * <p>用 mockStatic(StoreContext) 控制上下文（getStoreId / isProgrammaticIgnore），单测拦截器在各状态下的
 * ignoreTable 决策，确保不会误伤全 app SQL。</p>
 *
 * <p>放行判据是 {@link StoreContext#isProgrammaticIgnore()}（编程式 ignore() 段），<b>不是</b>
 * {@link StoreContext#isIgnore()}（含超管/租管身份）—— 铁律 4 option B：显式选店优先于身份。</p>
 *
 * @author djs
 * @since STORE-PERM-001
 */
@Tag("local")
@Tag("dev")
@DisplayName("StoreLineHandler 安全铁律单元测试")
class StoreLineHandlerTest {

    private StoreLineHandler newHandler(List<String> includes) {
        StoreProperties props = new StoreProperties();
        props.setIncludes(includes);
        return new StoreLineHandler(props);
    }

    @Test
    @DisplayName("铁律1: 非白名单表 → 一律 ignore（不过滤），即便上下文存在")
    void testIgnore_NotInWhitelist() {
        StoreLineHandler handler = newHandler(List.of("t_store_sale_record"));
        try (MockedStatic<StoreContext> mocked = mockStatic(StoreContext.class)) {
            mocked.when(StoreContext::getStoreId).thenReturn("100");
            mocked.when(StoreContext::isIgnore).thenReturn(false);

            // 非白名单：ruoyi 自带表 / 其他域表 / sys_* 一律放行
            assertThat(handler.ignoreTable("sys_user")).isTrue();
            assertThat(handler.ignoreTable("t_breed_pig")).isTrue();
            assertThat(handler.ignoreTable("t_warehouse_demand_manage")).isTrue();
        }
    }

    @Test
    @DisplayName("铁律2: 白名单表 + 空上下文 → ignore（不过滤），防清空数据")
    void testIgnore_EmptyContext() {
        StoreLineHandler handler = newHandler(List.of("t_store_sale_record"));
        try (MockedStatic<StoreContext> mocked = mockStatic(StoreContext.class)) {
            mocked.when(StoreContext::isIgnore).thenReturn(false);
            mocked.when(StoreContext::getStoreId).thenReturn(null);
            assertThat(handler.ignoreTable("t_store_sale_record")).isTrue();

            mocked.when(StoreContext::getStoreId).thenReturn("");
            assertThat(handler.ignoreTable("t_store_sale_record")).isTrue();
        }
    }

    @Test
    @DisplayName("铁律3: 白名单表 + 编程式 ignore() 段 → ignore（聚合/跨门店视图看全部门店）")
    void testIgnore_IgnoreFlag() {
        StoreLineHandler handler = newHandler(List.of("t_store_sale_record"));
        try (MockedStatic<StoreContext> mocked = mockStatic(StoreContext.class)) {
            mocked.when(StoreContext::getStoreId).thenReturn("100");
            mocked.when(StoreContext::isProgrammaticIgnore).thenReturn(true);
            assertThat(handler.ignoreTable("t_store_sale_record")).isTrue();
        }
    }

    @Test
    @DisplayName("铁律4(option B): 超管/租管身份不放行 —— 显式选了门店就按该店过滤")
    void testFilter_AdminIdentityDoesNotBypass() {
        StoreLineHandler handler = newHandler(List.of("t_store_sale_record"));
        try (MockedStatic<StoreContext> mocked = mockStatic(StoreContext.class)) {
            mocked.when(StoreContext::getStoreId).thenReturn("100");
            // 身份维度的 isIgnore（超管/租管）为 true，但没进编程式 ignore() 段
            mocked.when(StoreContext::isIgnore).thenReturn(true);
            mocked.when(StoreContext::isProgrammaticIgnore).thenReturn(false);
            assertThat(handler.ignoreTable("t_store_sale_record"))
                .as("显式选店优先于身份：超管也只看所选门店")
                .isFalse();
        }
    }

    @Test
    @DisplayName("白名单表 + 有效上下文 + 非放行 → 不 ignore（加 store_id 过滤）；大小写不敏感")
    void testFilter_Active() {
        StoreLineHandler handler = newHandler(List.of("t_store_sale_record"));
        try (MockedStatic<StoreContext> mocked = mockStatic(StoreContext.class)) {
            mocked.when(StoreContext::getStoreId).thenReturn("100");
            mocked.when(StoreContext::isIgnore).thenReturn(false);
            assertThat(handler.ignoreTable("t_store_sale_record")).isFalse();
            // 大小写不敏感
            assertThat(handler.ignoreTable("T_STORE_SALE_RECORD")).isFalse();
        }
    }

    @Test
    @DisplayName("非法门店 ID（非数字）→ ignore + 不抛异常，避免污染全部 SQL")
    void testIgnore_NonNumericStoreId() {
        StoreLineHandler handler = newHandler(List.of("t_store_sale_record"));
        try (MockedStatic<StoreContext> mocked = mockStatic(StoreContext.class)) {
            mocked.when(StoreContext::isIgnore).thenReturn(false);
            mocked.when(StoreContext::getStoreId).thenReturn("abc");
            assertThat(handler.ignoreTable("t_store_sale_record")).isTrue();
        }
    }

    @Test
    @DisplayName("getTenantId: 返门店 ID 的 LongValue 表达式")
    void testGetTenantId() {
        StoreLineHandler handler = newHandler(List.of("t_store_sale_record"));
        try (MockedStatic<StoreContext> mocked = mockStatic(StoreContext.class)) {
            mocked.when(StoreContext::getStoreId).thenReturn("12345");
            Expression expr = handler.getTenantId();
            assertThat(expr).isInstanceOf(LongValue.class);
            assertThat(((LongValue) expr).getValue()).isEqualTo(12345L);
        }
        assertThat(handler.getTenantIdColumn()).isEqualTo("store_id");
    }

    @Test
    @DisplayName("tableName 为 null → ignore")
    void testIgnore_NullTable() {
        StoreLineHandler handler = newHandler(List.of("t_store_sale_record"));
        assertThat(handler.ignoreTable(null)).isTrue();
    }
}
