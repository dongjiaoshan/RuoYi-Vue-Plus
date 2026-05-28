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
 *   <li>admin 类角色 → 4 个 board（manage / breed / plant / warehouse）</li>
 *   <li>breed_worker → 单 board（breed）</li>
 *   <li>warehouse_worker → 单 board（warehouse）</li>
 *   <li>多角色合集 → 多 board 并集</li>
 *   <li>store_admin（V1 推 V2）→ 空 board 集</li>
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
    @DisplayName("admin / boss / manager → 4 board 含 manage")
    void adminGetsFourBoardsIncludingManage() throws Exception {
        for (String adminRole : List.of("system_admin", "boss", "manager", "admin")) {
            Set<String> boards = mapBoards(Set.of(adminRole));
            assertEquals(4, boards.size(), "admin role " + adminRole + " 应得 4 板块");
            assertTrue(boards.contains("manage"), adminRole + " 应有 manage");
            assertTrue(boards.contains("breed"), adminRole + " 应有 breed");
            assertTrue(boards.contains("plant"), adminRole + " 应有 plant");
            assertTrue(boards.contains("warehouse"), adminRole + " 应有 warehouse");
            assertTrue(!boards.contains("store"), adminRole + " 不应有 store（V2 推后）");
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
    @DisplayName("store_admin → V1 空板块（store 推 V2）")
    void storeAdminEmpty() throws Exception {
        Set<String> boards = mapBoards(Set.of("store_admin"));
        assertTrue(boards.isEmpty(), "store_admin V1 不分配任何板块");
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
