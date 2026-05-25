-- ============================================================
-- BRD-EVENT-002 配种 / 分娩 / 断奶 / 查情 / 返空（菜单 + 按钮权限）
--   父菜单 7000 (养殖) 已在 SYS-AUTH-001 占位
--   段表（CLAUDE.md §6 + D04 closing 二级分段）：7120-7129 业务事件录入
--     7120-7123 配种记录（admin 只读列表 + 查询/详情/新增/导出按钮）
--     7124-7126 分娩记录
--     7127-7129 查情/返空/断奶记录（合并一个二级目录）
--   权限串与后端 BreedingController / FarrowController / WeaningController /
--   HeatController / NullReturnController @SaCheckPermission 严格一致
--   注：主入口在 mp 端，admin 端只做历史只读 + 导出
-- ============================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- ---- 配种记录 ----
  (7120, '配种记录', 7000, 20, 'breeding', 'djs-breed/event/breeding/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:event:breeding:list', 'log', 1, NOW(), 'BRD-EVENT-002'),
  (7121, '配种查询', 7120, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:breeding:list',   '#', 1, NOW(), 'BRD-EVENT-002'),
  (7122, '配种新增', 7120, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:breeding',        '#', 1, NOW(), 'BRD-EVENT-002'),
  (7123, '配种导出', 7120, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:breeding:export', '#', 1, NOW(), 'BRD-EVENT-002'),

  -- ---- 分娩记录 ----
  (7124, '分娩记录', 7000, 30, 'farrow', 'djs-breed/event/farrow/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:event:farrow:list', 'log', 1, NOW(), 'BRD-EVENT-002'),
  (7125, '分娩查询', 7124, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:farrow:list',  '#', 1, NOW(), 'BRD-EVENT-002'),
  (7126, '分娩新增', 7124, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:farrow',       '#', 1, NOW(), 'BRD-EVENT-002'),

  -- ---- 查情 / 返空 / 断奶 ----
  (7127, '查情返空断奶', 7000, 40, 'heat', 'djs-breed/event/heat/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:event:heat:list', 'log', 1, NOW(), 'BRD-EVENT-002'),
  (7128, '查情查询', 7127, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:heat:list',        '#', 1, NOW(), 'BRD-EVENT-002'),
  (7129, '查情/返空/断奶新增', 7127, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:heat',             '#', 1, NOW(), 'BRD-EVENT-002');

-- ------------------------------------------------------------
-- 角色 → 菜单映射
--   list / query (7120/7121, 7124/7125, 7127/7128): boss / manager / breed_admin / breed_worker / vet
--   写权限 (7122, 7126, 7129): boss / manager / breed_admin / breed_worker / vet
--   export (7123): boss / manager / breed_admin
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 7120 AS menu_id UNION SELECT 7121 UNION SELECT 7122
    UNION SELECT 7124 UNION SELECT 7125 UNION SELECT 7126
    UNION SELECT 7127 UNION SELECT 7128 UNION SELECT 7129
) m
WHERE r.role_key IN ('boss', 'manager', 'breed_admin', 'breed_worker', 'vet');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 7123
FROM sys_role r
WHERE r.role_key IN ('boss', 'manager', 'breed_admin');
