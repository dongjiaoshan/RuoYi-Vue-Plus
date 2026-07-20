-- ============================================================
-- FIX-WMS-LOC-OVERVIEW-001  各库位情况（库位聚合卡片抽独立二级菜单）  菜单 seed
-- ============================================================
-- 决策 D-FIX-6 B3：库位聚合卡片（8 类库存总览）从「库位管理」页移除，单独成一个二级菜单
-- 父菜单 9000 仓库 已由 WMS-MD-001 seed
-- menu_id 段：仓库域 9000-9999，库位 + 库存查询段 9010-9025；本 ticket 取段内空位 9026/9027
--   9026 各库位情况（C，复用 LocationCardGrid，路由 location-overview）
--   9027 查看（F，perms=djs:warehouse:location:summary，给 /summary 接口留权限口子）
-- 复用现有 /djs/warehouse/location/summary 接口，无 Java 改动
-- role_menu 白名单：role_id NOT IN (1, 101) AND del_flag='0'（与库位管理同范围业务角色命中）
--                   + 超管 role_id=1 全绑（对齐 STR-OP-001 范式）
-- sys_menu / sys_role_menu 为 ruoyi 框架表，无 tenant_id
-- ============================================================

-- ------------------------------------------------------------
-- 1. sys_menu seed 9026 / 9027
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 二级菜单：各库位情况（C 类，独立路由 + 组件，复用 LocationCardGrid 聚合卡）
    (9026, '各库位情况', 9000, 3, 'location-overview', 'djs-warehouse/location-overview/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:location:summary', 'chart', 1, NOW(), 'FIX-WMS-LOC-OVERVIEW-001'),

    -- 查看按钮权限（F，给 /summary 接口留 @SaCheckPermission 口子）
    (9027, '总览查看', 9026, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:location:summary', '#', 1, NOW(), 'FIX-WMS-LOC-OVERVIEW-001');

-- ------------------------------------------------------------
-- 2. role_menu 白名单绑定（role_id NOT IN (1, 101) — superadmin/system_admin perms 通配）
--    与库位管理同范围业务角色（boss/manager/warehouse_admin 等）命中
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id BETWEEN 9026 AND 9027;

-- 超级管理员角色 1 全绑
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id BETWEEN 9026 AND 9027;
