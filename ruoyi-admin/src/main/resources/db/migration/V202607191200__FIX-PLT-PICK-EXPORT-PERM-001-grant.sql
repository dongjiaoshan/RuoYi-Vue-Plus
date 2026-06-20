-- 采摘计划导出权限 djs:plant:pick:export（修复列表/调整抽屉导出 404）
-- 复用 8085（原游客采摘活动新增按钮，已于 V202606290900 删除，号段空闲），挂采摘计划列表 8082 下 F 型按钮
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (8085, '采摘计划导出', 8082, 6, '', '', '', 1, 0, 'F', '0', '0', 'djs:plant:pick:export', '#', 1, NOW(), 'FIX 采摘计划/调整抽屉导出权限');
-- 凡能看采摘计划列表(8082)的角色都授予导出
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 8085 FROM sys_role_menu rm WHERE rm.menu_id = 8082;
