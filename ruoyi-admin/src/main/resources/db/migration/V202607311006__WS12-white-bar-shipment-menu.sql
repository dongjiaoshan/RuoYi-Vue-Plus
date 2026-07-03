-- ============================================================
-- WS12 (row133)  白条发货记录  菜单 seed
-- ============================================================
-- 「产品生产记录」9301（M）下新增「白条发货记录」列表页：统计每日白条到发货月台 / 仓库出库情况。
-- 数据源 = t_warehouse_product_production 中 belong_type='white_bar' 的出库记录（只读列表）。
--
-- menu_id 段：仓库域 9000-9999，产品生产记录子段（9230-9248）空位 9231/9232/9235/9239/9242-9248。
--   取 9231（紧邻同级「产品生产」9230）：
--   C 菜单，parent=9301，path=whiteBarShipment，
--   component=djs-warehouse/production/whiteBarShipment/index，perms=djs:warehouse:whiteBarShipment:*
--
-- role_menu 白名单：业务角色 role_id NOT IN (1) 全绑（含 101 系统管理员，对齐同级 9230）+ 超管 role_id=1 全绑。
-- sys_menu / sys_role_menu 为 ruoyi 框架表，无 tenant_id。
-- ============================================================

-- ------------------------------------------------------------
-- 1. sys_menu seed 9231
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9231, '白条发货记录', 9301, 2, 'whiteBarShipment', 'djs-warehouse/production/whiteBarShipment/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:whiteBarShipment:list', 'documentation', 1, NOW(), 'WS12');

-- ------------------------------------------------------------
-- 2. role_menu 白名单绑定（业务角色 role_id NOT IN (1) 全绑）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1)
  AND r.del_flag = '0'
  AND m.menu_id = 9231;

-- 超级管理员角色 1 全绑
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id = 9231;
