-- mp 仓库 → 蔬菜处理 → 采摘活动 入口加权限控制（Kevin 2026-07-09）。
-- 原卡片 perm='' 恒显、角色管理里无对应权限项；现挂 djs:applet:warehouse:vegHandle:pickActivity，
-- 在「仓库小程序 → 蔬菜处理」下建 F 菜单 11072，角色管理可勾选配置。
-- 顺带补 warehouse_worker(110) 漏授的 11060（毛菜处理 mp 权限，与 warehouse_admin 对齐）。
-- 幂等：sys_menu 走 ON DUPLICATE KEY，sys_role_menu 走 INSERT IGNORE。

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
  (11072, '采摘活动', 11041, 3, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:warehouse:vegHandle:pickActivity', '#', 1, NOW(), 'DJS-PERM-PICKACT mp仓库蔬菜处理采摘活动入口')
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), perms = VALUES(perms), menu_type = VALUES(menu_type);

-- 授权：拥有仓库 mp 板块的角色（system_admin / boss / manager / warehouse_admin / warehouse_worker）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
  (101, 11072), (102, 11072), (103, 11072), (106, 11072), (110, 11072),
  (110, 11060);
