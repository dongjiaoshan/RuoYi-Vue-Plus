-- ============================================================
-- STR-SPLIT-001  门店白条分割  菜单 seed（10300-10306 段）
-- ============================================================
-- 父菜单 10000 门店销售 已由 SYS-AUTH-001 seed
-- menu_id 段：10300-10399（门店收尾段，本 ticket 占 10300-10306，10304-10306 预留）
-- ADR-0006 菜单分治
-- 权限串：djs:store:split:{list,query,add,export}（与 be @SaCheckPermission 完全一致）
-- 门店白条分割 admin only，无 mp 子菜单
-- role_menu 白名单：role_id NOT IN (1, 101) AND del_flag='0'
--   （store_admin=107 / boss=102 / manager=103 命中）+ 超管 role_id=1 全绑
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. sys_menu seed 10300-10303（10304-10306 预留）
-- ------------------------------------------------------------
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 门店白条分割列表（C，admin BizTable 列表 + 录入弹窗 + 导出）
    (10300, '门店分割', 10000, 4, 'split', 'djs-store/split/index', '',
     1, 0, 'C', '0', '0', 'djs:store:split:list', 'scissor', 1, NOW(), 'STR-SPLIT-001 admin'),
    (10301, '分割查询', 10300, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:split:query', '#', 1, NOW(), 'STR-SPLIT-001'),
    (10302, '分割录入', 10300, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:split:add', '#', 1, NOW(), 'STR-SPLIT-001 门店再分录入'),
    (10303, '分割导出', 10300, 3, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:split:export', '#', 1, NOW(), 'STR-SPLIT-001');

-- ------------------------------------------------------------
-- 2. role_menu 白名单绑定（role_id NOT IN (1, 101) — superadmin/system_admin perms 通配）
--    门店相关 boss(102) / manager(103) / store_admin(107) 命中
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id BETWEEN 10300 AND 10306;

-- 超级管理员角色 1 全绑
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id BETWEEN 10300 AND 10306;
