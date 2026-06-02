-- STR-DASH-001 门店看板菜单。挂门店父菜单 10000 下，order_num=0 置顶。
-- 克隆 W22-006 仓库看板（9400）范式：1 条 C 菜单 + role_menu 白名单。
-- 纯只读聚合，无新业务表（数据源 t_store_sale_record + t_warehouse_demand_manage）。
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
   menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (10400, '门店看板', 10000, 0, 'dashboard', 'djs-store/dashboard/index', '', 1, 0,
   'C', '0', '0', 'djs:store:dashboard:view', 'dashboard', 1, NOW(), 'STR-DASH-001 门店首页 dashboard');

-- 门店域 admin 角色白名单（store_admin / boss / manager + 超管）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 10400 FROM sys_role
WHERE role_key IN ('store_admin', 'boss', 'manager') AND del_flag = '0';
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10400);
