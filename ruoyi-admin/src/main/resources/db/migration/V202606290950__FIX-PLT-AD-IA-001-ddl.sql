-- ============================================================
-- FIX-PLT-AD-IA-001  种植域 IA 收尾  纯 sys_menu seed
-- ============================================================
-- 范围：种植左侧菜单 4 处与原型 IA 纠偏（只动 sys_menu + 给新分组父绑 role_menu）。
--   1. 新建「采摘管理」M 分组父（8004），挂种植根 8000，紧接人员管理组。
--   2. 采摘活动 8084 与采摘计划 8081 重挂到新父下并列平级（采摘活动不再挂 8081）。
--      8081 保留 M 型作采摘计划列表/调整容器，8082/8083 仍挂 8081 不动。
--   3. 命名：8200「班组绩效」→「绩效管理」。
--   4. 向导隐藏：8075「种植计划创建」visible '0'→'1'（左侧菜单不显示，路由保留可达）。
-- 禁止：改 component / path / perms（除新建父）；删任何 menu；动子菜单已有 role_menu。
-- 分组父 menu_id 落种植域预留段 8004（撞号实测 0 命中；8005-8008 已占 ADMIN-MENU-IA-001）。
-- M 分组父规范：menu_type='M' / component=NULL / perms='' / is_frame=1
--               / path 唯一短串 / icon = ruoyi 内置图标。
-- role_menu 白名单：role_id NOT IN (1,101) AND del_flag='0' + 超管 role_id=1。
-- 灾害 8100 / 采收 8500 保留不动。CLAUDE.md §6 menu_id 分段 / ADR-0006。
-- 改后必跑 bash script/sql/djs/_post-init.sh flush redis（菜单/字典缓存）。
-- ============================================================

-- ------------------------------------------------------------
-- 撞号自检：8004 必须 0 命中，否则中止（照 V202606171100:10 范式）
-- ------------------------------------------------------------
-- SELECT count(*) FROM sys_menu WHERE menu_id = 8004;  -- 期望 0

-- ============================================================
-- 步骤 1 — 新增「采摘管理」M 分组父（8004）
-- ============================================================
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (8004, '采摘管理', 8000, 5, 'plt-pick-mgmt', NULL, '',
     1, 0, 'M', '0', '0', '', 'date', 1, NOW(), 'FIX-PLT-AD-IA-001');

-- ============================================================
-- 步骤 2 — 采摘活动 8084 / 采摘计划 8081 重挂到新父 8004 下并列平级
-- ============================================================
-- 采摘活动 8084：parent_id 8081 → 8004，与采摘计划平级（不再是采摘计划子节点）
UPDATE sys_menu SET parent_id = 8004, order_num = 1 WHERE menu_id = 8084;
-- 采摘计划 8081：parent_id 8000 → 8004（保留 M 型；8082 列表/8083 调整仍挂 8081 不动）
UPDATE sys_menu SET parent_id = 8004, order_num = 2 WHERE menu_id = 8081;

-- ============================================================
-- 步骤 3 — 命名：8200「班组绩效」→「绩效管理」
-- ============================================================
UPDATE sys_menu SET menu_name = '绩效管理' WHERE menu_id = 8200;

-- ============================================================
-- 步骤 4 — 向导隐藏：8075「种植计划创建」visible '0'→'1'（左侧菜单不显示，路由保留）
-- ============================================================
UPDATE sys_menu SET visible = '1' WHERE menu_id = 8075;

-- ============================================================
-- 步骤 5 — 给新「采摘管理」父 8004 绑 role_menu 白名单（抄 V202606171100:150-162 范式）
-- ============================================================
-- 白名单：role_id NOT IN (1,101) AND del_flag='0'（全 11 业务角色，与 8081-8089 一致）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id = 8004;

-- 超级管理员角色 1 全绑
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id = 8004;
