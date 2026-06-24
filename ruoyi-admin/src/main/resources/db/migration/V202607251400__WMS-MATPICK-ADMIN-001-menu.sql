-- ============================================================
-- WMS-MATPICK-ADMIN-001  生产物资领用（admin 镜像小程序物资领用，行55）  菜单 seed
-- ============================================================
-- 挂「产品生产管理 9301」（V202606171100__ADMIN-MENU-IA-001 已建）下。
-- menu_id 段：仓库域 9100-9114 包材/物资领用 + 出入库流水已占；本 ticket 取段内空位
--   9118 生产物资领用（C，path=matPick component=djs-warehouse/matPick/index perms=djs:warehouse:matPick:list）
--   9116 查询（F，perms=djs:warehouse:matPick:query）
--   9117 导出（F，perms=djs:warehouse:matPick:export）
-- 列表 list/pick/return/loss/feed 共用 /djs/warehouse/matPick（WarehouseMatIssueController）。
-- role_menu 白名单：role_id NOT IN (1, 101) AND del_flag='0'（业务角色）+ 超管 role_id=1 全绑。
-- sys_menu / sys_role_menu 为 ruoyi 框架表，无 tenant_id。
-- ============================================================

-- ------------------------------------------------------------
-- 1. sys_menu seed 9118 / 9116 / 9117
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 二级菜单：生产物资领用（C 类，独立路由 + 组件）
    (9118, '生产物资领用', 9301, 5, 'matPick', 'djs-warehouse/matPick/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:matPick:list', 'shopping', 1, NOW(), 'WMS-MATPICK-ADMIN-001'),

    -- 查询按钮权限
    (9116, '查询', 9118, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:matPick:query', '#', 1, NOW(), 'WMS-MATPICK-ADMIN-001'),

    -- 导出按钮权限
    (9117, '导出', 9118, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:matPick:export', '#', 1, NOW(), 'WMS-MATPICK-ADMIN-001');

-- ------------------------------------------------------------
-- 2. role_menu 白名单绑定（role_id NOT IN (1, 101)）+ 超管 role_id=1 全绑
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id IN (9118, 9116, 9117);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id IN (9118, 9116, 9117);
