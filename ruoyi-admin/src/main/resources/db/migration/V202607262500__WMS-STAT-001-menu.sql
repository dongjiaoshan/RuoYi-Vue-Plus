-- ============================================================
-- WMS-STAT-001  仓库统计指标（仓库日报表/作物日报表/仓库月报表）  菜单 seed
-- ============================================================
-- 邓博 admin row16/17/18：仓库统计 3 张落盘表的只读列表入口。
-- 挂「库存管理」9302 下；menu_id 段：仓库域 9000-9999，库存管理子段空位 9131/9132/9133
--   （9130 已被有机饲喂记录占，9126-9128 损耗总览占）。
--   9131 仓库日报表（C，path=warehouseStatDaily, component=djs-warehouse/warehouseStat/daily）
--   9132 作物日报表（C，path=warehouseStatCrop,  component=djs-warehouse/warehouseStat/crop）
--   9133 仓库月报表（C，path=warehouseStatMonthly,component=djs-warehouse/warehouseStat/monthly）
-- 3 个 C 菜单共用同一只读权限串 djs:warehouse:stat:list（含手动补算端点 trigger-aggregate 也走此串）。
-- role_menu 白名单：role_id NOT IN (1, 101) 业务角色 + 超管 role_id=1 全绑（对齐 LOSS-OVERVIEW 范式）。
-- sys_menu / sys_role_menu 为 ruoyi 框架表，无 tenant_id。
-- ============================================================

-- ------------------------------------------------------------
-- 1. sys_menu seed 9131 / 9132 / 9133
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9131, '仓库日报表', 9302, 14, 'warehouseStatDaily', 'djs-warehouse/warehouseStat/daily', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:stat:list', 'chart', 1, NOW(), 'WMS-STAT-001'),

    (9132, '作物日报表', 9302, 15, 'warehouseStatCrop', 'djs-warehouse/warehouseStat/crop', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:stat:list', 'chart', 1, NOW(), 'WMS-STAT-001'),

    (9133, '仓库月报表', 9302, 16, 'warehouseStatMonthly', 'djs-warehouse/warehouseStat/monthly', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:stat:list', 'chart', 1, NOW(), 'WMS-STAT-001');

-- ------------------------------------------------------------
-- 2. role_menu 白名单绑定（role_id NOT IN (1, 101) — superadmin/system_admin perms 通配）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id BETWEEN 9131 AND 9133;

-- 超级管理员角色 1 全绑
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id BETWEEN 9131 AND 9133;
