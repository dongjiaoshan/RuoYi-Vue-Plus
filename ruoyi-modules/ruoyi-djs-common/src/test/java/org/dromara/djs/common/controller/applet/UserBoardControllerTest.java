package org.dromara.djs.common.controller.applet;

import org.dromara.djs.common.controller.applet.UserBoardController.BoardVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UserBoardController 板块映射单测（角色配置驱动 perm → board，纯 POJO 无 Spring）。
 *
 * <p>WS3 改造后：板块可见性由用户菜单权限（角色管理里勾选的授权）推导，不再硬编码 role_key。
 * 门店板块 V1 mp 已下线（Kevin 2026-07-08），不映射。</p>
 */
@Tag("local")
@Tag("dev")
@DisplayName("UserBoardController 板块映射单测（perm 驱动）")
class UserBoardControllerTest {

    private final UserBoardController controller = new UserBoardController();

    @Test
    @DisplayName("超管 *:*:* → 全 4 板块（含 manage，不含已下线 store）")
    void superAdminAllBoards() {
        Set<String> boards = controller.mapPermsToBoards(Set.of("*:*:*"));
        assertEquals(Set.of("manage", "breed", "plant", "warehouse"), boards);
    }

    @Test
    @DisplayName("养殖权限 → 单板块 breed")
    void breedPermsOnlyBreed() {
        assertEquals(Set.of("breed"),
            controller.mapPermsToBoards(Set.of("djs:breed:barn:list", "djs:mptab:breed:home")));
    }

    @Test
    @DisplayName("仓库权限 → 单板块 warehouse")
    void warehousePermsOnlyWarehouse() {
        assertEquals(Set.of("warehouse"),
            controller.mapPermsToBoards(Set.of("djs:applet:warehouse:mat:pick")));
    }

    @Test
    @DisplayName("养殖 + 仓库权限 → 并集")
    void multiDomainUnion() {
        assertEquals(Set.of("breed", "warehouse"),
            controller.mapPermsToBoards(Set.of("djs:applet:breed:x", "djs:applet:warehouse:ship:y")));
    }

    @Test
    @DisplayName("含全部三业务域 → 追加 manage")
    void allThreeGetsManage() {
        Set<String> boards = controller.mapPermsToBoards(Set.of(
            "djs:applet:breed:x", "djs:applet:plant:pick:y", "djs:applet:warehouse:ship:z"));
        assertEquals(Set.of("breed", "plant", "warehouse", "manage"), boards);
    }

    @Test
    @DisplayName("空 / null 权限 → 无板块")
    void emptyPermsNoBoard() {
        assertTrue(controller.mapPermsToBoards(Set.of()).isEmpty());
        assertTrue(controller.mapPermsToBoards(null).isEmpty());
    }

    @Test
    @DisplayName("BoardVo 4 字段构造完整")
    void boardVoConstruction() {
        BoardVo manage = new BoardVo("manage", "管理", "i-carbon-data-vis-4", "/pages/breed/dashboard/index");
        assertEquals("manage", manage.getCode());
        assertEquals("管理", manage.getName());
        assertNotNull(manage.getIcon());
        assertNotNull(manage.getRoute());
    }
}
