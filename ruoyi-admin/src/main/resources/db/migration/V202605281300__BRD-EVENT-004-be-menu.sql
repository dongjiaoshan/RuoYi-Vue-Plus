-- ============================================================
-- BRD-EVENT-004 死亡 / 淘汰 / 阉割 / 转移 / 出栏（菜单 + 按钮权限）
--   父菜单 7000 (养殖) 已在 SYS-AUTH-001 占位
--   段表（CLAUDE.md §6 + D04 closing）：7130-7139 死淘转移出栏事件
--     7130-7132 死亡记录
--     7133-7135 淘汰记录
--     7136-7138 转移记录
--     7139      出栏记录（合并入口；阉割不开 admin 入口，仅 mp）
--   权限串与后端 Die/Eliminate/Castrate/Transfer/SlaughterController
--   @SaCheckPermission 严格一致
-- ============================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- ---- 死亡记录 ----
  (7130, '死亡记录', 7000, 50, 'die', 'djs-breed/event/die/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:event:die:list', 'log', 1, NOW(), 'BRD-EVENT-004'),
  (7131, '死亡查询', 7130, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:die:list',   '#', 1, NOW(), 'BRD-EVENT-004'),
  (7132, '死亡新增', 7130, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:die',        '#', 1, NOW(), 'BRD-EVENT-004'),

  -- ---- 淘汰记录 ----
  (7133, '淘汰记录', 7000, 60, 'eliminate', 'djs-breed/event/eliminate/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:event:eliminate:list', 'log', 1, NOW(), 'BRD-EVENT-004'),
  (7134, '淘汰查询', 7133, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:eliminate:list',  '#', 1, NOW(), 'BRD-EVENT-004'),
  (7135, '淘汰新增', 7133, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:eliminate',       '#', 1, NOW(), 'BRD-EVENT-004'),

  -- ---- 转移记录 ----
  (7136, '转移记录', 7000, 70, 'transfer', 'djs-breed/event/transfer/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:event:transfer:list', 'log', 1, NOW(), 'BRD-EVENT-004'),
  (7137, '转移查询', 7136, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:transfer:list',  '#', 1, NOW(), 'BRD-EVENT-004'),
  (7138, '转移新增', 7136, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:transfer',       '#', 1, NOW(), 'BRD-EVENT-004'),

  -- ---- 出栏记录 ----
  (7139, '出栏记录', 7000, 80, 'slaughter', 'djs-breed/event/slaughter/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:event:slaughter:list', 'log', 1, NOW(), 'BRD-EVENT-004');

-- ------------------------------------------------------------
-- 角色 → 菜单映射
--   list / query: boss / manager / breed_admin / breed_worker / vet 全可见
--   写权限 (新增 7132 / 7135 / 7138): boss / manager / breed_admin / breed_worker / vet
--   出栏列表 (7139): 只读，所有相关角色可见
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 7130 AS menu_id UNION SELECT 7131 UNION SELECT 7132
    UNION SELECT 7133 UNION SELECT 7134 UNION SELECT 7135
    UNION SELECT 7136 UNION SELECT 7137 UNION SELECT 7138
    UNION SELECT 7139
) m
WHERE r.role_key IN ('boss', 'manager', 'breed_admin', 'breed_worker', 'vet');

-- 阉割（mp 端独占；admin 不开入口）：权限仍授给 boss/manager/breed_admin/breed_worker/vet
-- 用 sys_menu_id=0（无菜单挂载）+ 字符串 perms 串通过 hasPermi 判断不可行；
-- 阉割权限走 sys_role_menu 不挂菜单更新走 sa-token role-based 不可行。
-- 简化：阉割端点 @SaCheckPermission 用复用现有 djs:breed:event:transfer / die 之外
-- 的独立 key 'djs:breed:event:castrate'，但因 mp 端 V1 不细化按钮权限
-- （登录态即默认全角色，详 ADR-0003），此 SQL 不为 castrate 创建独立菜单。
-- 后续 ADR 落地正式 RBAC 时再补 mp 端钮位级权限。
