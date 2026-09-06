-- ============================================================
-- V6 row179  管理板块底栏加「门店管理」tab + 角色权限
-- ============================================================
-- 甲方原话：「在仓库管理菜单右边加上门店管理版块，同时在角色权限里加上对应的权限信息」。
--
-- 结构（挂 mp 权限树「门店小程序」11050 下，与 11036 挂 11030、11043 挂 11040 同套路）
--   11050 门店小程序（已存在）
--     ├ 11051 需求下单   djs:mptab:store:demand      ← 门店板块 tab（店员用，本次不动）
--     │   └ 11052 门店需求·功能 djs:applet:store:demand:*
--     └ 11053 门店管理   djs:mptab:manage:store      ← 管理板块 tab 显隐（唯一 toggle）
--         └ 11054 门店管理·功能 djs:applet:manage:store:*  ← 月度看板端点
--
-- 🔴 权限串必须落在 manage 命名空间，不能用 djs:{mptab,applet}:store:*
--   UserBoardController.mapPermsToBoards 对 `djs:mptab:store` / `djs:applet:store` 前缀判
--   **门店板块**（店员板块）。管理者授了这两串，管理板块多一个 tab 的同时会凭空多出一整个门店板块。
--   djs:mptab:manage:store / djs:applet:manage:store:* 不命中任何 startsWith 分支，零副作用，
--   后端 UserBoardController 因此一行都不用改。
--
-- 11054 必须挂 11053 **下面**、不能平级：plus-ui 角色配置树走 buildMpTabLevelTree，11000 下第 2 层
--   （= 11050 的子节点）是 tab 级，其后代折叠进 hiddenDescendants，勾/取消 tab 时自动带上。
--   平级会让树上多出「门店管理·功能」这个内部技术名节点，且取消勾 tab 时端点权限撤不掉。
--
-- menu_type='F'：纯权限叶子，不产生 admin 路由（同 11036 / 11043）。M/C 型 path 不能为空，
--   F 型不产生路由故留空。visible='1' = 只在角色授权树可见、不进 admin 侧边栏。
--
-- 授权：按「当前谁进得了管理板块就授给谁」派生，不硬编码 role_id
--   （甲方自建角色是雪花 id，staging 与 prod 不一致，硬编码会在 prod 授错角色）。
--   判据对齐 UserBoardController.mapPermsToBoards：breed / plant / warehouse 三域齐全 → 有 manage 板块。
--   ADR-0020 §2.2「授权必授整子树含父目录」：同时补 11000（小程序权限根）+ 11050（门店小程序容器）,
--   这两个容器 perms 为空，补了不影响板块判定。
--
-- 生效：PermissionService 颁 token 实时读 DB，mp 重新登录即生效；无需 flush redis。
-- ============================================================

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
  (11053, '门店管理',      11050, 2, '', '', '', 1, 0, 'F', '1', '0', 'djs:mptab:manage:store',     '#',    1, NOW(), 'V6 row179 管理板块「门店管理」tab 显隐（唯一 toggle，功能叶子 11054 挂其下）'),
  (11054, '门店管理·功能', 11053, 1, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:manage:store:*',  '#',    1, NOW(), 'V6 row179 管理板块门店月度看板端点权限（挂 11053 下，随 tab 一起授/撤）')
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), perms = VALUES(perms), menu_type = VALUES(menu_type);

-- 授给「能进管理板块」的角色（三域齐全），连同两个父容器
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT t.role_id, n.menu_id FROM (
  SELECT rm.role_id
  FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id
  GROUP BY rm.role_id
  HAVING SUM(m.perms LIKE 'djs:applet:breed%'     OR m.perms LIKE 'djs:mptab:breed%')     > 0
     AND SUM(m.perms LIKE 'djs:applet:plant%'     OR m.perms LIKE 'djs:mptab:plant%')     > 0
     AND SUM(m.perms LIKE 'djs:applet:warehouse%' OR m.perms LIKE 'djs:mptab:warehouse%') > 0
) t
CROSS JOIN (SELECT 11000 AS menu_id UNION ALL SELECT 11050 UNION ALL SELECT 11053 UNION ALL SELECT 11054) n;

-- 超管一并授（走 *:*:* 不依赖这些行，纯为角色树勾选状态一致）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
  (1, 11000), (1, 11050), (1, 11053), (1, 11054);

-- 验证（按需手动跑）
-- SELECT menu_id, menu_name, parent_id, menu_type, perms FROM sys_menu WHERE menu_id IN (11050,11053,11054);
-- SELECT menu_id, menu_name FROM sys_menu WHERE parent_id = 11053;  -- 必须只有 11054
-- SELECT role_id, GROUP_CONCAT(menu_id ORDER BY menu_id) FROM sys_role_menu WHERE menu_id IN (11053,11054) GROUP BY role_id;
