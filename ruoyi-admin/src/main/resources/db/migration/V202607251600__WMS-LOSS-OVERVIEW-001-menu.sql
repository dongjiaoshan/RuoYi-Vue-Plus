-- ============================================================
-- WMS-LOSS-OVERVIEW-001  损耗总览（compute-on-read over t_warehouse_loss_flow）  菜单 seed
-- ============================================================
-- 仓库-admin 行63：每日损耗系统汇总（列表）+ 当日损耗产品明细（弹窗，含产品图）
-- 挂「库存管理」9302 下；menu_id 段：仓库域 9000-9999，库存管理子段取空位 9126/9127/9128
--   9126 损耗总览（C，path=lossOverview, component=djs-warehouse/lossOverview/index,
--         perms=djs:warehouse:loss:overview:list）
--   9127 查看明细（F，perms=djs:warehouse:loss:overview:query，给 /detail 接口留权限口子）
--   9128 导出（F，perms=djs:warehouse:loss:overview:export，预留导出权限口子）
-- role_menu 白名单：role_id NOT IN (1, 101) 业务角色 + 超管 role_id=1 全绑（对齐 FIX-WMS-LOC-OVERVIEW-001 范式）
-- sys_menu / sys_role_menu 为 ruoyi 框架表，无 tenant_id
-- ============================================================

-- ------------------------------------------------------------
-- 1. sys_menu seed 9126 / 9127 / 9128
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 二级菜单：损耗总览（C 类，独立路由 + 组件）
    (9126, '损耗总览', 9302, 12, 'lossOverview', 'djs-warehouse/lossOverview/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:loss:overview:list', 'documentation', 1, NOW(), 'WMS-LOSS-OVERVIEW-001'),

    -- 查看明细按钮权限（F，给 /detail 接口留 @SaCheckPermission 口子）
    (9127, '查看明细', 9126, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:loss:overview:query', '#', 1, NOW(), 'WMS-LOSS-OVERVIEW-001'),

    -- 导出按钮权限（F，预留）
    (9128, '导出', 9126, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:loss:overview:export', '#', 1, NOW(), 'WMS-LOSS-OVERVIEW-001');

-- ------------------------------------------------------------
-- 2. role_menu 白名单绑定（role_id NOT IN (1, 101) — superadmin/system_admin perms 通配）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id BETWEEN 9126 AND 9128;

-- 超级管理员角色 1 全绑
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id BETWEEN 9126 AND 9128;
