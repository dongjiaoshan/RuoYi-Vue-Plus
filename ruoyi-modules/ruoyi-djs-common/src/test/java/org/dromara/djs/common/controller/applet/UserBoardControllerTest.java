package org.dromara.djs.common.controller.applet;

import org.dromara.djs.common.controller.applet.UserBoardController.BoardVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UserBoardController 角色 → board 映射单测（不启 Spring，纯反射）。
 *
 * <p>覆盖 D9X DJS-FIX-MP-W22-001 后的新行为：</p>
 * <ul>
 *   <li>admin 类角色 → 5 个 board（manage / breed / plant / warehouse / store）</li>
 *   <li>breed_worker → 单 board（breed）</li>
 *   <li>warehouse_worker → 单 board（warehouse）</li>
 *   <li>多角色合集 → 多 board 并集</li>
 *   <li>store_admin → 单 board（store，STR-DEMAND-001 起开通门店板块）</li>
 * </ul>
 */
@Tag("local")
@Tag("dev")
@DisplayName("UserBoardController 板块映射单测")
class UserBoardControllerTest {

    private final UserBoardController controller = new UserBoardController();

    /** 反射调 private mapRolesToBoards */
    @SuppressWarnings("unchecked")
    private Set<String> mapBoards(Set<String> roleKeys) throws Exception {
        Method m = UserBoardController.class.getDeclaredMethod("mapRolesToBoards", Set.class);
        m.setAccessible(true);
        return (Set<String>) m.invoke(controller, roleKeys);
    }

    @Test
    @DisplayName("admin / boss / manager → 5 board 含 manage + store")
    void adminGetsFiveBoardsIncludingManageAndStore() throws Exception {
        for (String adminRole : List.of("system_admin", "boss", "manager", "admin")) {
            Set<String> boards = mapBoards(Set.of(adminRole));
            assertEquals(5, boards.size(), "admin role " + adminRole + " 应得 5 板块");
            assertTrue(boards.contains("manage"), adminRole + " 应有 manage");
            assertTrue(boards.contains("breed"), adminRole + " 应有 breed");
            assertTrue(boards.contains("plant"), adminRole + " 应有 plant");
            assertTrue(boards.contains("warehouse"), adminRole + " 应有 warehouse");
            assertTrue(boards.contains("store"), adminRole + " 应有 store（STR-DEMAND-001 起开通门店板块）");
        }
    }

    @Test
    @DisplayName("breed_worker → 单板块 breed")
    void breedWorkerOnlyBreed() throws Exception {
        Set<String> boards = mapBoards(Set.of("breed_worker"));
        assertEquals(Set.of("breed"), boards);
    }

    @Test
    @DisplayName("warehouse_worker → 单板块 warehouse")
    void warehouseWorkerOnlyWarehouse() throws Exception {
        Set<String> boards = mapBoards(Set.of("warehouse_worker"));
        assertEquals(Set.of("warehouse"), boards);
    }

    @Test
    @DisplayName("breed_admin + warehouse_worker → 多板块并集")
    void multiRoleUnion() throws Exception {
        Set<String> boards = mapBoards(Set.of("breed_admin", "warehouse_worker"));
        assertEquals(Set.of("breed", "warehouse"), boards);
    }

    @Test
    @DisplayName("store_admin / store_clerk → 单板块 store（STR-DEMAND-001 起开通）")
    void storeRoleGetsStore() throws Exception {
        assertEquals(Set.of("store"), mapBoards(Set.of("store_admin")), "store_admin 应得 store 板块");
        assertEquals(Set.of("store"), mapBoards(Set.of("store_clerk")), "store_clerk 应得 store 板块");
    }

    @Test
    @DisplayName("BoardVo 4 字段构造完整 + 等值")
    void boardVoConstruction() {
        BoardVo manage = new BoardVo("manage", "管理", "i-carbon-data-vis-4", "/pages/breed/dashboard/index");
        assertEquals("manage", manage.getCode());
        assertEquals("管理", manage.getName());
        assertNotNull(manage.getIcon());
        assertNotNull(manage.getRoute());
    }
}
