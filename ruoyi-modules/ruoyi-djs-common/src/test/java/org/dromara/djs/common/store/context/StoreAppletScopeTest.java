package org.dromara.djs.common.store.context;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.mock.SaStorageForMock;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.store.service.IStoreUserRelationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 使用真实请求上下文及真实 SQL 解析，证明范围在聚合前生效且请求结束清理。 */
@Tag("local")
@Tag("dev")
class StoreAppletScopeTest {

    private MockedStatic<SaHolder> holder;
    private IStoreUserRelationService relations;
    private StoreContextInterceptor web;
    private StoreScopeInnerInterceptor sql;
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        holder = mockStatic(SaHolder.class);
        holder.when(SaHolder::getStorage).thenReturn(new SaStorageForMock());
        relations = mock(IStoreUserRelationService.class);
        web = new StoreContextInterceptor(relations);
        StoreProperties properties = new StoreProperties();
        properties.setIncludes(List.of("t_store_daily_ledger", "t_store_sale_record", "t_store_return",
            "t_warehouse_demand_manage"));
        sql = new StoreScopeInnerInterceptor(new StoreLineHandler(properties));
    }

    @AfterEach
    void tearDown() {
        holder.close();
    }

    private MockHttpServletRequest request(String route, String storeId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", route);
        if (storeId != null) {
            request.setParameter("storeId", storeId);
        }
        return request;
    }

    @Test
    void boundStoreAllowedAndForeignStoreRejectedBeforeReading() {
        when(relations.currentAccessibleStoreIds()).thenReturn(List.of(11L));
        for (String path : List.of("manage/monthly", "manage/detail", "dashboard/daily",
            "dashboard/summary", "demand/list", "demand/day-list", "demand/day-detail", "demand/catalog")) {
            MockHttpServletRequest allowed = request("/djs/applet/store/" + path, "11");
            assertThat(web.preHandle(allowed, response, new Object())).isTrue();
            assertThat(StoreContext.getAccessibleStoreIds()).containsExactly(11L);
            web.afterCompletion(allowed, response, new Object(), null);
            assertThatThrownBy(() -> web.preHandle(request("/djs/applet/store/" + path, "22"), response,
                new Object())).isInstanceOf(ServiceException.class).hasMessageContaining("无该门店操作权限");
        }
    }

    @Test
    void encodedRequestPathsUseMatchedRouteAndCannotEscapeStoreScope() {
        when(relations.currentAccessibleStoreIds()).thenReturn(List.of(11L));
        for (String route : List.of("/djs/applet/%73tore/manage/monthly", "/%64js/applet/store/manage/monthly",
            "/djs/%61pplet/store/manage/monthly")) {
            MockHttpServletRequest request = request(route, "22");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/djs/applet/store/manage/monthly");
            assertThatThrownBy(() -> web.preHandle(request, response, new Object()))
                .isInstanceOf(ServiceException.class).hasMessageContaining("无该门店操作权限");
        }
        MockHttpServletRequest withoutMvcAttribute = request("/djs/applet/%73tore/manage/monthly", "22");
        assertThatThrownBy(() -> web.preHandle(withoutMvcAttribute, response, new Object()))
            .isInstanceOf(ServiceException.class);
    }

    @Test
    void omittedStoreAggregatesOnlyBoundStoresIncludingEveryUnionBranch() {
        when(relations.currentAccessibleStoreIds()).thenReturn(List.of(11L, 12L));
        web.preHandle(request("/djs/applet/store/manage/monthly", null), response, new Object());
        String parsed = sql.parserSingle("SELECT SUM(x.qty) FROM ("
            + "SELECT SUM(l.sale_qty) qty FROM t_store_daily_ledger l UNION ALL "
            + "SELECT SUM(d.demand_quantity) qty FROM t_warehouse_demand_manage d UNION ALL "
            + "SELECT SUM(r.return_quantity) qty FROM t_store_return r) x", null);
        assertThat(parsed).contains("l.store_id IN (11, 12)", "d.store_id IN (11, 12)",
            "r.store_id IN (11, 12)");
    }

    @Test
    void noBindingsProducesEmptyScopeInsteadOfGlobalTotals() {
        when(relations.currentAccessibleStoreIds()).thenReturn(List.of());
        web.preHandle(request("/djs/applet/store/dashboard/summary", null), response, new Object());
        assertThat(sql.parserSingle("SELECT SUM(s.sale_amount) FROM t_store_sale_record s", null))
            .contains("1 = 0");
        assertThat(sql.parserSingle("SELECT COUNT(*) FROM t_warehouse_demand_manage", null))
            .contains("1 = 0");
    }

    @Test
    void idOnlyReadsAndWritesCannotEscapeAuthorizedStores() {
        StoreContext.setAccessibleStoreIds(List.of(11L));
        assertThat(sql.parserSingle("SELECT * FROM t_warehouse_demand_manage WHERE id = 999", null))
            .contains("store_id IN (11)");
        assertThat(sql.parserSingle("UPDATE t_warehouse_demand_manage SET demand_quantity = 3 WHERE id = 999", null))
            .contains("store_id IN (11)");
        assertThat(sql.parserSingle("DELETE FROM t_warehouse_demand_manage WHERE id = 999", null))
            .contains("store_id IN (11)");
        // 多门店权限不能成为 INSERT 的单店值；业务写入口会先校验 body.storeId。
        assertThat(sql.parserSingle("INSERT INTO t_warehouse_demand_manage (id, store_id) VALUES (1, 11)", null))
            .doesNotContain("IN (");
    }

    @Test
    void wallOffOrAdminLeavesAppletUnrestrictedAndDoesNotRequireStoreHeader() {
        when(relations.currentAccessibleStoreIds()).thenReturn(null);
        assertThat(web.preHandle(request("/djs/applet/store/dashboard/daily", null), response, new Object()))
            .isTrue();
        assertThat(sql.parserSingle("SELECT * FROM t_store_sale_record", null)).doesNotContain("WHERE");
    }

    @Test
    void malformedStoreCannotDisableFilteringAndHeaderCannotExpandScope() {
        when(relations.currentAccessibleStoreIds()).thenReturn(List.of(11L));
        assertThatThrownBy(() -> web.preHandle(request("/djs/applet/store/manage/monthly", "invalid"), response,
            new Object())).isInstanceOf(ServiceException.class);
        MockHttpServletRequest request = request("/djs/applet/store/manage/monthly", null);
        request.addHeader(StoreContext.HEADER_STORE_ID, "22");
        web.preHandle(request, response, new Object());
        assertThat(StoreContext.getStoreId()).isNull();
        assertThat(sql.parserSingle("SELECT * FROM t_store_daily_ledger", null)).contains("store_id IN (11)");
    }

    @Test
    void warehouseReturnsDoNotAcquireStoreClerkScope() {
        assertThat(web.preHandle(request("/applet/store/return/pending", null), response, new Object())).isTrue();
        verifyNoInteractions(relations);
        assertThat(StoreContext.getAccessibleStoreIds()).isNull();
        assertThat(sql.parserSingle("SELECT * FROM t_store_return", null)).doesNotContain("WHERE");
    }

    @Test
    void completionCleansScopeEvenOnBusinessFailureAndAdminFilteringIsUnchanged() {
        when(relations.currentAccessibleStoreIds()).thenReturn(List.of(11L));
        MockHttpServletRequest request = request("/djs/applet/store/manage/detail", null);
        web.preHandle(request, response, new Object());
        web.afterCompletion(request, response, new Object(), new IllegalStateException("business failure"));
        assertThat(StoreContext.getAccessibleStoreIds()).isNull();
        assertThat(StoreContext.getStoreId()).isNull();
        StoreContext.setStoreId("22");
        assertThat(sql.parserSingle("SELECT * FROM t_store_daily_ledger l", null)).contains("l.store_id = 22");
        assertThat(sql.parserSingle("SELECT * FROM sys_user", null)).doesNotContain("store_id");
    }
}
