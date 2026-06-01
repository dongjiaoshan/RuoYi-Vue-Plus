-- ============================================================
-- STR-DEMAND-001  门店端发起需求  菜单 seed
-- ============================================================
-- 范围：门店店长（admin PC）+ 门店店员（mp）发起采购需求
--       复用 t_warehouse_demand_manage 表 + IDemandManageService（0 新表 0 新字典）
-- 父菜单 10000 门店销售 已由 SYS-AUTH-001 seed
-- menu_id 段：10001-10009（门店域 10000-10099；需求录入占首段）
-- ADR-0006 菜单分治：门店+追溯+DSH 域 10000-10999
-- role_menu 白名单：role_id NOT IN (1, 101) AND del_flag='0'（store_admin=107 / store_clerk=112 命中）
--                   + 超管 role_id=1 全绑
-- ============================================================

-- ------------------------------------------------------------
-- 1. sys_menu seed 10001-10009（9 行：1 父 M + 1 列表 C + 7 权限 F）
-- ------------------------------------------------------------
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 门店需求父菜单（M，挂在门店销售 10000 下）
    (10001, '门店需求', 10000, 1, 'demand', NULL, '',
     1, 0, 'M', '0', '0', '', 'documentation', 1, NOW(), 'STR-DEMAND-001'),
    -- 需求列表（C，admin 列表页）
    (10002, '需求录入', 10001, 1, 'list', 'djs-store/demand/index', '',
     1, 0, 'C', '0', '0', 'djs:store:demand:list', 'edit', 1, NOW(), 'STR-DEMAND-001'),

    -- 7 权限按钮（F）
    (10003, '需求查询', 10002, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:demand:query', '#', 1, NOW(), 'STR-DEMAND-001'),
    (10004, '需求新增', 10002, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:demand:add', '#', 1, NOW(), 'STR-DEMAND-001'),
    (10005, '需求修改', 10002, 3, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:demand:edit', '#', 1, NOW(), 'STR-DEMAND-001'),
    (10006, '需求删除', 10002, 4, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:demand:remove', '#', 1, NOW(), 'STR-DEMAND-001'),
    (10007, '取消需求', 10002, 5, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:demand:cancel', '#', 1, NOW(), 'STR-DEMAND-001'),
    (10008, '指定猪只', 10002, 6, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:demand:assign_pig', '#', 1, NOW(), 'STR-DEMAND-001'),
    -- 列表权限节点（与 10002 C 同串，便于按钮级 v-hasPermi 复用）
    (10009, '需求列表', 10002, 7, '', '', '', 1, 0, 'F', '0', '0',
     'djs:store:demand:list', '#', 1, NOW(), 'STR-DEMAND-001');

-- ------------------------------------------------------------
-- 2. role_menu 白名单绑定（role_id NOT IN (1, 101) — superadmin/system_admin perms 通配）
--    门店相关 store_admin(107) / store_clerk(112) 命中
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id BETWEEN 10001 AND 10009;

-- 超级管理员角色 1 全绑
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id BETWEEN 10001 AND 10009;
