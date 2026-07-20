-- DJS-PERM-MENU-004：种植小程序(11030) 权限树收成 tab 级（菜单级 RBAC）。机制同 DJS-PERM-MENU-003（Sa-Token 通配，零 controller 改动、零 403）。
--
-- 种植 mp 底栏 4 tab = 播种 / 采收 / 农事 / 我的。
--   播种(pages/plant/index)：调 /applet/plant/plan|dashboard|crop|activity，这些 applet controller 无 @SaCheckPermission（登录即可）→ tab 不 gate。
--   我的：人人可见，不 gate。
-- 故只需 2 个 gate 节点：
--   采收 → djs:applet:plant:pick:*   （submit/summary/list/detail）
--   农事 → djs:applet:plant:work:* + djs:applet:plant:team:list（农事录入 + 班组下拉 picker）

-- 1) 新建种植 mp tab 权限节点（11031-11034）。采收=F；农事=M 容器（path 非空避免空 path 崩 router）下挂 2 个 F 通配。
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
  (11031, '采收',     11030, 1, '',              '', '', 1, 0, 'F', '1', '0', 'djs:applet:plant:pick:*',   '#',    1, NOW(), 'DJS-PERM-MENU-004 种植 mp tab：采收'),
  (11032, '农事',     11030, 2, 'mp-plant-work', NULL, '', 1, 0, 'M', '1', '0', '',                          'tree', 1, NOW(), 'DJS-PERM-MENU-004 种植 mp tab：农事'),
  (11033, '农事录入', 11032, 1, '',              '', '', 1, 0, 'F', '1', '0', 'djs:applet:plant:work:*',   '#',    1, NOW(), 'DJS-PERM-MENU-004 空地/生长/灾害/移栽/中央分发/我的记录'),
  (11034, '班组下拉', 11032, 2, '',              '', '', 1, 0, 'F', '1', '0', 'djs:applet:plant:team:list','#',    1, NOW(), 'DJS-PERM-MENU-004 班组 picker');

-- 2) 授权：沿用当前 11030 颗粒度权限的角色集
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r CROSS JOIN sys_menu m
WHERE r.role_id IN (101, 102, 103, 105, 108, 111)
  AND m.menu_id IN (11031, 11032, 11033, 11034);

-- 3) 删除颗粒度叶子（perms 已被通配覆盖）
DELETE FROM sys_role_menu WHERE menu_id IN (8088, 8089, 8091, 8092, 8093, 8094, 8095, 8096, 8097, 8501, 8502);
DELETE FROM sys_menu      WHERE menu_id IN (8088, 8089, 8091, 8092, 8093, 8094, 8095, 8096, 8097, 8501, 8502);
