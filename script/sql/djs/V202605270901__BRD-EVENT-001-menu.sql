-- ============================================================
-- BRD-EVENT-001 引种登记（菜单 + 按钮权限）
--   父菜单 7000 (养殖) 已在 SYS-AUTH-001 占位
--   本文件分配 7100-7109 段（CLAUDE.md §6 段表：7100-7199 = BRD-EVENT-001~005 业务事件）
--     7100 二级目录：引种登记（admin 查看历史 + 业务）
--     7101-7105 三级按钮
--   权限串 djs:breed:event:intro* 与后端 PigIntroController @SaCheckPermission 严格一致
--   注：引种主入口在 mp 端（CameraUpload 拍照 + 表单），admin 端只做列表查看 / 导出
-- ============================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 二级目录：引种登记
  (7100, '引种登记', 7000, 10, 'intro', 'djs-breed/event/intro/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:event:intro:list', 'log', 1, NOW(), 'BRD-EVENT-001'),

  -- 三级按钮（与 controller @SaCheckPermission 一一对应）
  (7101, '引种查询', 7100, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:intro:list',   '#', 1, NOW(), 'BRD-EVENT-001'),
  (7102, '引种详情', 7100, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:intro:query',  '#', 1, NOW(), 'BRD-EVENT-001'),
  (7103, '引种登记', 7100, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:intro',        '#', 1, NOW(), 'BRD-EVENT-001'),
  (7104, '引种导出', 7100, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:intro:export', '#', 1, NOW(), 'BRD-EVENT-001');

-- ------------------------------------------------------------
-- 角色 → 菜单映射（对齐当前项目角色：boss / manager / breed_admin / breed_worker / vet）
--   list / query (7100, 7101, 7102): boss / manager / breed_admin / breed_worker（养殖员录入端会查自己引的猪）
--   intro 写权限 (7103): boss / manager / breed_admin / breed_worker
--   export (7104): boss / manager / breed_admin
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 7100 AS menu_id UNION SELECT 7101 UNION SELECT 7102 UNION SELECT 7103) m
WHERE r.role_key IN ('boss', 'manager', 'breed_admin', 'breed_worker');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 7104
FROM sys_role r
WHERE r.role_key IN ('boss', 'manager', 'breed_admin');
