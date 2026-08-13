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
 * 门店板块 V6 row62/63 重新上线（{@code djs:applet:store} / {@code djs:mptab:store} 前缀）。</p>
 */
@Tag("local")
@Tag("dev")
@DisplayName("UserBoardController 板块映射单测（perm 驱动）")
class UserBoardControllerTest {

    private final UserBoardController controller = new UserBoardController();

    @Test
    @DisplayName("超管 *:*:* → 全 5 板块（含 manage + store）")
    void superAdminAllBoards() {
        Set<String> boards = controller.mapPermsToBoards(Set.of("*:*:*"));
        assertEquals(Set.of("manage", "breed", "plant", "store", "warehouse"), boards);
    }

    @Test
    @DisplayName("门店 mp 权限 → 单板块 store（11051 tab / 11052 功能两种前缀都认）")
    void storePermsOnlyStore() {
        assertEquals(Set.of("store"),
            controller.mapPermsToBoards(Set.of("djs:mptab:store:demand")));
        assertEquals(Set.of("store"),
            controller.mapPermsToBoards(Set.of("djs:applet:store:demand:day-list")));
    }

    @Test
    @DisplayName("admin 门店权限 djs:store:* 不映射成 mp 门店板块（只认 applet/mptab 命名空间）")
    void adminStorePermsDoNotLeakIntoBoards() {
        assertTrue(controller.mapPermsToBoards(Set.of("djs:store:demand:list", "djs:store:op:edit")).isEmpty());
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
    @DisplayName("manage 判定不看门店：三业务域齐全但无门店仍得 manage；只有门店不得 manage")
    void manageIgnoresStoreBoard() {
        assertEquals(Set.of("breed", "plant", "warehouse", "manage"), controller.mapPermsToBoards(Set.of(
            "djs:applet:breed:x", "djs:applet:plant:pick:y", "djs:applet:warehouse:ship:z")));
        assertEquals(Set.of("store"), controller.mapPermsToBoards(Set.of("djs:mptab:store:demand")));
        assertEquals(Set.of("breed", "plant", "store", "warehouse", "manage"), controller.mapPermsToBoards(Set.of(
            "djs:applet:breed:x", "djs:applet:plant:pick:y", "djs:applet:warehouse:ship:z",
            "djs:mptab:store:demand")));
    }

    /**
     * 前缀判定必须是 {@code startsWith("djs:applet:store")}，不能退化成
     * 「含 applet 且包含 store」这类子串匹配。
     *
     * <p>{@code djs:applet:common:store:list} 是**跨板块共享**的门店 picker 权限（menu 5350，
     * 授给全部业务角色 104-112）。子串匹配会让每一个持有它的角色——包括系统管理员、仓库工人——
     * 在小程序板块页凭空多出一个门店板块。独立验收实测过：把判定换成
     * {@code startsWith("djs:applet") && contains("store")} 后，现有用例全部照样通过（变异存活），
     * 而真实 system_admin 的板块从 4 个变成 5 个。这条就是补那个洞。</p>
     */
    @Test
    @DisplayName("djs:applet:common:store:list（共享门店 picker）不得判出门店板块")
    void sharedStorePickerPermDoesNotGrantStoreBoard() {
        assertEquals(Set.of(), controller.mapPermsToBoards(Set.of("djs:applet:common:store:list")));
        // 与真实 system_admin 一致：持共享 picker + 三业务域 → 板块里不能有 store
        assertEquals(Set.of("breed", "plant", "warehouse", "manage"),
            controller.mapPermsToBoards(Set.of(
                "djs:applet:common:store:list",
                "djs:applet:breed:pig:list",
                "djs:applet:plant:pick:list",
                "djs:applet:warehouse:mat:pick")));
    }

    @Test
    @DisplayName("门店板块只认 djs:applet:store / djs:mptab:store 两个前缀")
    void storeBoardOnlyFromOwnNamespaces() {
        assertEquals(Set.of("store"), controller.mapPermsToBoards(Set.of("djs:applet:store:demand:*")));
        assertEquals(Set.of("store"), controller.mapPermsToBoards(Set.of("djs:mptab:store:demand")));
        // admin 域的门店权限（非 mp 命名空间）不得判出 mp 板块
        assertEquals(Set.of(), controller.mapPermsToBoards(Set.of("djs:store:demand:list")));
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
