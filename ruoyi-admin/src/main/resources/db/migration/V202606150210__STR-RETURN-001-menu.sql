-- ============================================================
-- STR-RETURN-001  门店退回管理  菜单 seed（10350-10356 段）
-- ============================================================
-- 父菜单 10000 门店销售 已由 SYS-AUTH-001 seed
-- menu_id 段：10350-10379（门店+追溯+DSH 域 10000-10999，本 ticket 占门店收尾退货子段）
-- ⚠️ 严禁复用 9210 段（那是 WMS-SHIP-001 仓库退货，仓库板块）
-- ADR-0006 菜单分治
-- 权限串：djs:store:return:{list,query,add,edit,remove,export}（与 be @SaCheckPermission 完全一致）
-- 门店退回 admin only，无 mp 子菜单
-- role_menu 白名单：role_id NOT IN (1, 101) AND del_flag='0'
--   （store_admin=107 / boss=102 / manager=103 命中）+ 超管 role_id=1 全绑
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. sys_menu seed 10350-10356
-- ------------------------------------------------------------
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 门店退回列表（C，admin BizTable 三方向退回登记）
    (10350, '门店退回', 10000, 6, 'return', 'djs-store/return/index', '',
     1, 0, 'C', '0', '0', 'djs:store:return:list', 'documentation', 1, NOW(), 'STR-RETURN-001 admin'),
    (10351, '退回查询', 10350, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:return:query', '#', 1, NOW(), 'STR-RETURN-001'),
    (10352, '退回新增', 10350, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:return:add', '#', 1, NOW(), 'STR-RETURN-001 三方向登记'),
    (10353, '退回修改', 10350, 3, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:return:edit', '#', 1, NOW(), 'STR-RETURN-001 不改 return_no'),
    (10354, '退回删除', 10350, 4, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:return:remove', '#', 1, NOW(), 'STR-RETURN-001 软删'),
    (10355, '退回导出', 10350, 5, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:return:export', '#', 1, NOW(), 'STR-RETURN-001');

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
  AND m.menu_id BETWEEN 10350 AND 10356;

-- 超级管理员角色 1 全绑
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id BETWEEN 10350 AND 10356;
