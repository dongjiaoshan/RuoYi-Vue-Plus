-- ============================================================
-- STR-STOCK-001  门店盘点  菜单 seed（10200-10206 段）
-- ============================================================
-- 父菜单 10000 门店销售 已由 SYS-AUTH-001 seed
-- menu_id 段：10200-10299（门店+追溯+DSH 域 10000-10999，本 ticket 占门店盘点段）
-- ADR-0006 菜单分治
-- 权限串：djs:store:check:{list,query,add,complete,cancel,export}（与 be @SaCheckPermission 完全一致）
-- 门店盘点 admin only，无 mp 子菜单
-- role_menu 白名单：role_id NOT IN (1, 101) AND del_flag='0'
--   （store_admin=107 / boss=102 / manager=103 命中）+ 超管 role_id=1 全绑
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. sys_menu seed 10200-10206
-- ------------------------------------------------------------
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 门店盘点列表（C，admin BizTable 盘点单列表）
    (10200, '门店盘点', 10000, 3, 'check', 'djs-store/check/index', '',
     1, 0, 'C', '0', '0', 'djs:store:check:list', 'list', 1, NOW(), 'STR-STOCK-001 admin'),
    (10201, '盘点查询', 10200, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:check:query', '#', 1, NOW(), 'STR-STOCK-001'),
    (10202, '新建盘点', 10200, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:check:add', '#', 1, NOW(), 'STR-STOCK-001 新建并锁门店'),
    (10203, '完成盘点', 10200, 3, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:check:complete', '#', 1, NOW(), 'STR-STOCK-001 落差异 + 解锁'),
    (10204, '取消盘点', 10200, 4, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:check:cancel', '#', 1, NOW(), 'STR-STOCK-001 解锁不回写'),
    (10205, '盘点导出', 10200, 5, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:check:export', '#', 1, NOW(), 'STR-STOCK-001');

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
  AND m.menu_id BETWEEN 10200 AND 10206;

-- 超级管理员角色 1 全绑
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id BETWEEN 10200 AND 10206;
