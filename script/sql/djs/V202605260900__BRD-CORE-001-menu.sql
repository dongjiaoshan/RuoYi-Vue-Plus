-- ============================================================
-- BRD-CORE-001 猪只主表 + 状态机引擎（菜单 + 按钮权限）
--   父菜单 7000 (养殖) 已在 SYS-AUTH-001 V202605201100 占位
--   本文件分配 7200-7299 段（参 .claude/CLAUDE.md §6 第 6 条段表）
--     7200 二级目录：猪只主表（admin 列表 + 详情）
--     7201-7204 三级按钮：查询 / 详情 / 历史 / 触发事件
--   权限串 djs:breed:pig:* 与后端 PigController @SaCheckPermission 严格一致
--   注：djs:breed:pig:event 是通用事件入口，仅赋系统级角色（boss / 调试），
--   不下放普通用户；BRD-EVENT-* 子 ticket 自己写业务端点 + 内部调 fireEvent
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 菜单：猪只主表（挂养殖目录 7000 下，order_num=50 排在育种 / 农场 / 药品 / 公猪 / 用药计划之后）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 二级目录：猪只主表（admin 列表 + 详情 + 历史）
  (7200, '猪只主表', 7000, 50, 'pig', 'djs-breed/pig/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:pig:list', 'monitor', 1, NOW(), 'BRD-CORE-001'),

  -- 三级按钮权限（4 个，与后端 PigController @SaCheckPermission 严格一致）
  (7201, '猪只查询', 7200, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pig:list',  '#', 1, NOW(), 'BRD-CORE-001'),
  (7202, '猪只详情', 7200, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pig:query', '#', 1, NOW(), 'BRD-CORE-001'),
  (7203, '触发事件', 7200, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pig:event', '#', 1, NOW(), 'BRD-CORE-001'),
  (7204, '猪只导出', 7200, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pig:export', '#', 1, NOW(), 'BRD-CORE-001');

-- ------------------------------------------------------------
-- 角色 → 菜单映射
--   boss / manager / breed_admin / breed_worker 全部赋猪只主表 list + query
--   djs:breed:pig:event 仅赋 boss（系统级 / 调试），manager / 业务角色不下放
--   djs:breed:pig:export 赋 boss + manager
-- ------------------------------------------------------------
-- list (7201) + query (7202)：所有养殖相关角色
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 7200 AS menu_id UNION SELECT 7201 UNION SELECT 7202) m
WHERE r.role_key IN ('boss', 'manager', 'breed_admin', 'breed_worker');

-- event (7203)：仅 boss（系统级）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 7203
FROM sys_role r
WHERE r.role_key IN ('boss');

-- export (7204)：boss + manager
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 7204
FROM sys_role r
WHERE r.role_key IN ('boss', 'manager');
