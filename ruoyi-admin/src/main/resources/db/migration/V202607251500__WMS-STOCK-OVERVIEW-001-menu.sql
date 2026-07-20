-- ============================================================
-- WMS-STOCK-OVERVIEW-001  库存总览（每日库存系统汇总 + 按日下钻产品库存情况）  菜单 seed
-- ============================================================
-- 仓库-admin 行56：每日库存系统汇总列表 + 详情（compute-on-read 回放 stock_flow，Kevin 拍板）
-- 父菜单 9302 库存管理（由 ADMIN-MENU-IA-001 seed）
-- menu_id 段：仓库域 9000-9999，本 ticket 取 9123/9124/9125
--   9123 库存总览（C，path=stockOverview，component=djs-warehouse/stockOverview/index，
--        perms=djs:warehouse:stockOverview:list）
--   9124 详情查询（F，perms=djs:warehouse:stockOverview:query）
--   9125 导出（F，perms=djs:warehouse:stockOverview:export）
-- role_menu 白名单：role_id NOT IN (1, 101) AND del_flag='0'（业务角色）+ 超管 role_id=1 全绑
-- sys_menu / sys_role_menu 为 ruoyi 框架表，无 tenant_id
-- ============================================================

-- ------------------------------------------------------------
-- 1. sys_menu seed 9123 / 9124 / 9125
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 二级菜单：库存总览（C 类，独立路由 + 组件）
    (9123, '库存总览', 9302, 8, 'stockOverview', 'djs-warehouse/stockOverview/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:stockOverview:list', 'chart', 1, NOW(), 'WMS-STOCK-OVERVIEW-001'),

    -- 详情查询按钮权限（F，给 /detail 接口留 @SaCheckPermission 口子）
    (9124, '总览详情', 9123, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:stockOverview:query', '#', 1, NOW(), 'WMS-STOCK-OVERVIEW-001'),

    -- 导出按钮权限（F，给 /export 接口留口子）
    (9125, '总览导出', 9123, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:stockOverview:export', '#', 1, NOW(), 'WMS-STOCK-OVERVIEW-001');

-- ------------------------------------------------------------
-- 2. role_menu 白名单绑定（role_id NOT IN (1, 101) — superadmin/system_admin perms 通配）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id BETWEEN 9123 AND 9125;

-- 超级管理员角色 1 全绑
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id BETWEEN 9123 AND 9125;
