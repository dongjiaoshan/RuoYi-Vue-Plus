-- ============================================================
-- STR-OP-001  门店经营（产品关联 + 销售明细）  菜单 seed
-- ============================================================
-- 父菜单 10000 门店销售 已由 SYS-AUTH-001 seed
-- menu_id 段：10100-10199（门店+追溯+DSH 域 10000-10999，本 ticket 占门店经营段）
-- ADR-0006 菜单分治
-- 权限串：djs:storeRelation:* （产品关联）+ djs:storeSale:* （经营明细）
-- role_menu 白名单：role_id NOT IN (1, 101) AND del_flag='0'（store_admin=107 / boss=102 / manager=103 命中）
--                   + 超管 role_id=1 全绑
-- 本 ticket admin 端，不绑 mp 角色 store_clerk
-- ============================================================

-- ------------------------------------------------------------
-- 1. sys_menu seed 10100-10199
-- ------------------------------------------------------------
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 门店经营父菜单（M，挂在门店销售 10000 下）
    (10100, '门店经营', 10000, 2, 'operation', NULL, '',
     1, 0, 'M', '0', '0', '', 'shopping', 1, NOW(), 'STR-OP-001'),

    -- 产品关联（C，admin 列表页 el-transfer）
    (10110, '产品关联', 10100, 1, 'relation', 'djs-store/operation/relation/index', '',
     1, 0, 'C', '0', '0', 'djs:storeRelation:list', 'link', 1, NOW(), 'STR-OP-001'),
    (10111, '关联查询', 10110, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:storeRelation:query', '#', 1, NOW(), 'STR-OP-001'),
    (10112, '关联保存', 10110, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:storeRelation:edit', '#', 1, NOW(), 'STR-OP-001'),

    -- 经营明细（C，admin BizTable 销售流水列表）
    (10120, '经营明细', 10100, 2, 'sale', 'djs-store/operation/sale/index', '',
     1, 0, 'C', '0', '0', 'djs:storeSale:list', 'money', 1, NOW(), 'STR-OP-001'),
    (10121, '明细查询', 10120, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:storeSale:query', '#', 1, NOW(), 'STR-OP-001'),
    (10122, '明细新增', 10120, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:storeSale:add', '#', 1, NOW(), 'STR-OP-001'),
    (10123, '明细删除', 10120, 3, '', '', '', 1, 0, 'F', '0', '0',
     'djs:storeSale:remove', '#', 1, NOW(), 'STR-OP-001'),
    (10124, '明细导入', 10120, 4, '', '', '', 1, 0, 'F', '0', '0',
     'djs:storeSale:import', '#', 1, NOW(), 'STR-OP-001');

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
  AND m.menu_id BETWEEN 10100 AND 10199;

-- 超级管理员角色 1 全绑
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id BETWEEN 10100 AND 10199;
