-- ============================================================
-- WMS-FLOW-001  出入库记录查询 + mp 包材库管理  菜单 seed
-- ============================================================
-- 范围：admin 入库记录页 / 出库记录页（复用 t_warehouse_stock_flow，不新建表）
--       + mp 包材库管理 3 子页（belong_type='package' 复用 D9 WMS-MAT-001 字典）
-- menu_id 段：9240-9249（仓库 9000-9999；避开 9000-9180 D7-D9 / 9200-9235 D10）
-- 不含字典 seed：belong_type='package' 已由 V202606071300 WMS-MAT-001 seed，本 ticket 直接复用
-- role_menu 白名单：role_id NOT IN (1, 101) AND del_flag='0'
-- ============================================================

-- ------------------------------------------------------------
-- 1. sys_menu seed 9240-9248（9 行）
--    9240 入库记录（C，admin 列表）/ 9242 查询F / 9243 导出F
--    9241 出库记录（C，admin 列表）/ 9244 查询F / 9245 导出F
--    9246 包材库（M，mp 父占位）/ 9247 mp 列表权限 / 9248 mp 详情权限
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- admin 入库记录页（C）
    (9240, '入库记录', 9000, 40, 'flow/in', 'djs-warehouse/flow/in/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:flowIn:list', 'download', 1, NOW(), 'WMS-FLOW-001 admin'),
    -- admin 出库记录页（C）
    (9241, '出库记录', 9000, 41, 'flow/out', 'djs-warehouse/flow/out/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:flowOut:list', 'upload', 1, NOW(), 'WMS-FLOW-001 admin'),

    -- 入库记录 2 权限按钮（F）
    (9242, '入库记录查询', 9240, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:flowIn:list', '#', 1, NOW(), 'WMS-FLOW-001 admin'),
    (9243, '入库记录导出', 9240, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:flowIn:export', '#', 1, NOW(), 'WMS-FLOW-001 admin'),

    -- 出库记录 2 权限按钮（F）
    (9244, '出库记录查询', 9241, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:flowOut:list', '#', 1, NOW(), 'WMS-FLOW-001 admin'),
    (9245, '出库记录导出', 9241, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:flowOut:export', '#', 1, NOW(), 'WMS-FLOW-001 admin'),

    -- mp 包材库父菜单（M 占位，mp 权限挂于此）
    (9246, '包材库', 9000, 46, '#', '', '',
     1, 0, 'M', '0', '0', '', 'box', 1, NOW(), 'WMS-FLOW-001 mp'),
    -- mp 包材库 2 权限（列表 + 详情；今日总览随列表权限）
    (9247, 'mp 包材列表', 9246, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:applet:warehouse:packing:list', '#', 1, NOW(), 'WMS-FLOW-001 mp'),
    (9248, 'mp 包材详情', 9246, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:applet:warehouse:packing:detail', '#', 1, NOW(), 'WMS-FLOW-001 mp');

-- ------------------------------------------------------------
-- 2. role_menu 白名单绑定（role_id NOT IN (1, 101) — superadmin/system_admin perms 通配）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id IN (9240, 9241, 9242, 9243, 9244, 9245, 9246, 9247, 9248);
