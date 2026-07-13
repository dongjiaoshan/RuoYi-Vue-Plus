-- row7：仓库角色进入「采摘活动记录」页加载班组下拉时报「没有访问权限」。
-- 采摘活动记录（pages/plant/work/harvest）需要班组下拉权限 djs:applet:plant:team:list（菜单 11034）。
-- 权限重建脚本 DJS-PERM-REBUILD-001 重灌角色菜单时，仓库管理员(106)/仓库工人(110) 未包含 11034 → 缺该只读权限。
-- 补授 11034 给两个仓库角色。INSERT IGNORE 幂等，已有行不重复。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
  (106, 11034),
  (110, 11034);
