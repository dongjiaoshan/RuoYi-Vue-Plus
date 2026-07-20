-- PLT-DASH-001 种植首页 dashboard 菜单（CLAUDE.md §6 种植域 8100-8199 dashboard 段）。
-- 8100-8102 已被 PLT-WORK-003 灾害记录占用，本 ticket 顺移到段内 8110（父 C 菜单）+ 8111（view 权限）。
-- 8110 父 C 菜单（component=djs-plant/dashboard/index，order_num=0 置顶）+ 8111 view 权限。
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
   menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (8110, '种植看板', 8000, 0, 'dashboard', 'djs-plant/dashboard/index', '', 1, 0,
   'C', '0', '0', 'djs:plant:dashboard:view', 'dashboard', 1, NOW(), 'PLT-DASH-001 种植首页 dashboard'),
  (8111, '种植看板查看', 8110, 1, '', '', '', 1, 0,
   'F', '0', '0', 'djs:plant:dashboard:view', '#', 1, NOW(), 'PLT-DASH-001 view 权限');

-- 授予所有已持有「地块管理(8010)」的角色（照 W22-006 范式，使种植看板与现有种植菜单可见性一致）。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 8110 FROM sys_role_menu WHERE menu_id = 8010;
