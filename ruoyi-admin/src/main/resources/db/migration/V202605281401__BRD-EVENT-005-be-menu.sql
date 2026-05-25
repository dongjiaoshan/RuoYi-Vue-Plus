-- ============================================================
-- BRD-EVENT-005 生长记录（菜单 + 按钮权限）
--   父菜单 7000 (养殖) 已在 SYS-AUTH-001 占位
--   段表（CLAUDE.md §6 + D04 closing）：7140-7149 生长记录
--     7140 生长记录主菜单
--     7141 生长查询
--     7142 生长新增（admin 端录背膘/背高）
--     7143 生长删除（3 天内可删）
--     7144 生长导出
--   权限串与后端 PigGrowthController @SaCheckPermission 严格一致
-- ============================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7140, '生长记录', 7000, 90, 'growth', 'djs-breed/event/growth/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:event:growth:list', 'log', 1, NOW(), 'BRD-EVENT-005'),
  (7141, '生长查询', 7140, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:growth:list',   '#', 1, NOW(), 'BRD-EVENT-005'),
  (7142, '生长新增', 7140, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:growth:add',    '#', 1, NOW(), 'BRD-EVENT-005'),
  (7143, '生长删除', 7140, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:growth:remove', '#', 1, NOW(), 'BRD-EVENT-005'),
  (7144, '生长导出', 7140, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:growth:export', '#', 1, NOW(), 'BRD-EVENT-005');

-- ------------------------------------------------------------
-- 角色 → 菜单映射
--   list / query (7140/7141): boss / manager / breed_admin / breed_worker / vet
--   add / remove (7142/7143): boss / manager / breed_admin / breed_worker
--   export (7144): boss / manager / breed_admin
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 7140 AS menu_id UNION SELECT 7141
) m
WHERE r.role_key IN ('boss', 'manager', 'breed_admin', 'breed_worker', 'vet');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 7142 AS menu_id UNION SELECT 7143
) m
WHERE r.role_key IN ('boss', 'manager', 'breed_admin', 'breed_worker');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 7144
FROM sys_role r
WHERE r.role_key IN ('boss', 'manager', 'breed_admin');
