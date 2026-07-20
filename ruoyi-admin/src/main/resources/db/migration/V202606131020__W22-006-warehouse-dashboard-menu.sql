-- DJS-FIX-ADMIN-W22-006 hotfix：仓库看板菜单补登记。
--
-- W22-006 建了 plus-ui `djs-warehouse/index.vue` 仓库 dashboard（今日需求量 / 今日生产 / 最近盘点
-- 三 KPI 卡 + 库位概览表），但**漏 seed 对应菜单** → 仪表盘视图无任何 sys_menu 指向、admin 不可达
-- （仓库父菜单 9000 是 M 目录、component=NULL，点击仅展开子项不导航）。D11 WMS-FLOW-001 人力测试
-- 5.2 暴露「找不到三卡在哪个页面」。
--
-- 对齐养殖域 7400「养殖看板」范式：在 9000 下加 9400「仓库看板」（C，component djs-warehouse/index，
-- order_num=0 置顶）。CLAUDE.md §6 仓库域 9400-9499 留作 dashboard 段。
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
   menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (9400, '仓库看板', 9000, 0, 'dashboard', 'djs-warehouse/index', '', 1, 0,
   'C', '0', '0', 'djs:warehouse:dashboard:view', 'dashboard', 1, NOW(), 'W22-006 仓库 dashboard 菜单补登记');

-- 授予所有已持有「库位管理(9010)」的角色，使仓库看板与现有仓库菜单可见性一致（superadmin 不依赖此）。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 9400 FROM sys_role_menu WHERE menu_id = 9010;
