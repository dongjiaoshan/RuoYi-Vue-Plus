-- ============================================================
-- FIX-ADMIN-R130  毛菜间处理记录：admin 菜单 + 按钮权限 seed
-- ============================================================
-- 仓库「库存管理」9302 下新增「毛菜间处理记录」只读列表页（C），order_num=11 排在
--   「有机饲喂记录」（9130，order_num=10）之后。
-- 列表 = 毛菜处理间处理流水（t_warehouse_handle_record record_type=2）
--        + 毛菜处理结算损耗（t_warehouse_vegetable_handle.loss_weight）
--        + 采摘活动流水（t_plant_plant_activity）三支 UNION。
-- menu_id 段：仓库域 9000-9999，9130 已被「有机饲喂记录」占用，取 9131-9133。
-- 「处理方式」复用现有字典 djs_pick_dest（sale/veg_fresh/platform/loss/feed），本迁移不建新字典。
-- 「统计来源」沿用「采摘明细」页（8085）口径的裸码值 1=毛菜处理间 / 2=采摘活动，前端 i18n 映射，无字典。
-- sys_menu / sys_role_menu 为 ruoyi 框架表，无 tenant_id。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. sys_menu 9131：毛菜间处理记录（C，挂库存管理 9302）
--    perms 用通配 djs:warehouse:vegHandleRecord:*（Sa-Token vagueMatch + 前端 checkPermi 均支持），
--    与同组「有机饲喂记录」9130 / 「采摘明细」8085 一致。
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9131, '毛菜间处理记录', 9302, 11, 'vegHandleRecord', 'djs-warehouse/vegHandleRecord/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:vegHandleRecord:*', 'documentation', 1, NOW(), 'FIX-ADMIN-R130');

-- ------------------------------------------------------------
-- 2. 按钮级权限（F，挂 9131）：查询 + 导出
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9132, '毛菜间处理记录查询', 9131, 1, '', '', '',
     1, 0, 'F', '0', '0', 'djs:warehouse:vegHandleRecord:list', '#', 1, NOW(), 'FIX-ADMIN-R130'),
    (9133, '毛菜间处理记录导出', 9131, 2, '', '', '',
     1, 0, 'F', '0', '0', 'djs:warehouse:vegHandleRecord:export', '#', 1, NOW(), 'FIX-ADMIN-R130');

-- ------------------------------------------------------------
-- 3. sys_role_menu：照抄「有机饲喂记录」9130 的授权角色集合（超管 1 + 业务角色 102/103/106/110）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, m.menu_id
FROM sys_role_menu rm
CROSS JOIN (SELECT 9131 AS menu_id UNION ALL SELECT 9132 UNION ALL SELECT 9133) m
WHERE rm.menu_id = 9130;
