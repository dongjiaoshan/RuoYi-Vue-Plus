-- ============================================================
-- V6 row182  角色权限树补「管理小程序」分组节点
-- ============================================================
-- 甲方原话：「角色权限里缺了管理端权限的配置，无法配置管理端权限」。
--
-- 管理板块四个 tab 的权限本来就都存在，只是分散挂在各自领域的小程序节点下，
-- 角色配置树上找不到一个叫「管理」的地方，甲方就当成"没有这个配置"。
-- 本次只做**树的重组**：不新增 / 不改 / 不删任何 perms 串，四个 tab 的权限语义完全不动。
--
-- 结构（11000 小程序权限根下，与五个领域小程序并列的第六个容器）
--   11000 小程序权限
--     ├ 11010 通用小程序 / 11020 养殖小程序 / 11030 种植小程序 / 11040 仓库小程序 / 11050 门店小程序
--     └ 11080 管理小程序                       ← 本次新增（纯容器，perms 空）
--         ├ 11025 养殖管理 djs:mptab:breed:dashboard
--         ├ 11036 种植管理 djs:mptab:plant:dashboard
--         ├ 11043 仓库管理 djs:mptab:warehouse:dashboard
--         └ 11053 门店管理 djs:mptab:manage:store
--               └ 11054 门店管理·功能 djs:applet:manage:store:*（仍挂 11053，不动）
--
-- 🔴 深度必须停在「11000 下第 2 层」：plus-ui 角色配置树走 buildMpTabLevelTree
--   （src/views/system/role/index.vue），11000 子树只显示到 depthFromMp = 2，更深的后代折叠进
--   hiddenDescendants 由父 tab 代授。11080 = 第 1 层容器 ⇒ 四个 tab 落在第 2 层，照常显示；
--   11054 落在第 3 层被折叠，与它今天挂 11053 下的表现完全一致。
--   再包一层（如 11080 下再分域）会把四个 tab 压到第 3 层直接从树上消失，比现状更糟。
--
-- 🔴 mp 侧零影响：底栏 tab（miniapp/src/tabbar/config.ts）与后端板块判定
--   （UserBoardController.mapPermsToBoards）都只 startsWith 匹配 perms 串，不看 parent_id。
--   perms 一个字没动 ⇒ 板块可见性、tab 显隐、端点鉴权全部维持原样。
--
-- 🔴 sys_role_menu 不需要迁移：它引用的是 menu_id，四个 tab 的 menu_id 没变，既有授权自动跟着走。
--
-- 11080 取号：mp 权限树段 11000-11999，现占用到 11073，11080 留空档且不撞号。
-- 行的形状（path / component / query_param / icon / visible）对齐同层容器 11020 / 11050。
-- visible='1' = 只在角色授权树里可见，不进 admin 侧边栏。
--
-- 幂等：INSERT ... ON DUPLICATE KEY UPDATE + UPDATE 幂等 + INSERT IGNORE，可重复执行。
-- 生效：PermissionService 颁 token 实时读 DB；admin 角色树是每次打开现查，无需 flush redis。
-- ============================================================

-- 1) 容器节点（纯分组，无 perms）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
  (11080, '管理小程序', 11000, 6, 'mp-manage', NULL, '', 1, 0, 'M', '1', '0', '', 'tree', 1, NOW(), 'V6 row182 管理板块四个 tab 的权限容器（自身无 perms，仅分组）')
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
                        path = VALUES(path), menu_type = VALUES(menu_type), perms = VALUES(perms), icon = VALUES(icon);

-- 2) 四个管理 tab 改挂 11080（perms / menu_type / 名称一概不动，只改父与排序）
UPDATE sys_menu SET parent_id = 11080, order_num = 1 WHERE menu_id = 11025;  -- 养殖管理
UPDATE sys_menu SET parent_id = 11080, order_num = 2 WHERE menu_id = 11036;  -- 种植管理
UPDATE sys_menu SET parent_id = 11080, order_num = 3 WHERE menu_id = 11043;  -- 仓库管理
UPDATE sys_menu SET parent_id = 11080, order_num = 4 WHERE menu_id = 11053;  -- 门店管理（11054 仍挂其下）

-- 3) 已持有任一管理 tab 的角色补授容器 11080（ADR-0020 §2.2：授权必授整子树含父目录）
--    容器 perms 为空，补它不改变任何板块判定，只让角色树的勾选状态连得起来。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 11080 FROM sys_role_menu rm WHERE rm.menu_id IN (11025, 11036, 11043, 11053);

-- 超管一并授（走 *:*:* 不依赖这行，纯为角色树勾选状态一致）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 11080);

-- 验证（按需手动跑）
-- SELECT menu_id, parent_id, menu_name, perms FROM sys_menu WHERE parent_id IN (11000, 11080) ORDER BY parent_id, order_num;
-- SELECT menu_id, menu_name FROM sys_menu WHERE parent_id = 11053;  -- 必须仍只有 11054
