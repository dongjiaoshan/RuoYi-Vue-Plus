-- V6-R43 菜单：系统管理 →「数据管理」目录 →「燎毛间产品重量调整」
-- 号段：系统底座 5000-5999，取 5700-5702（5690-5760 整段当前无任何 sys_menu 行占用，不会继承历史授权）。

-- 1) 腾位：系统管理下 order_num >= 9 的整体后移一位，让「数据管理」紧跟「字典管理」(order_num=8) 之后。
--    幂等守卫：只在「数据管理」尚未建立时腾位 —— 裸 UPDATE 重跑会把同一批菜单再次后移，
--    排序越跑越散（下面的 INSERT IGNORE 本身幂等，唯独这一句不是）。
SET @menu_exists := (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 5700);
SET @ddl := IF(@menu_exists = 0,
    'UPDATE sys_menu SET order_num = order_num + 1 WHERE parent_id = 1 AND order_num >= 9',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 菜单 seed
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (5700, '数据管理', 1, 9, 'data-manage', '', '',
     1, 0, 'M', '0', '0',
     '', 'documentation', 1, NOW(), 'V6-R43'),
    (5701, '燎毛间产品重量调整', 5700, 1, 'burn-adjust', 'djs-warehouse/burnAdjust/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:burnAdjust:list', 'edit', 1, NOW(), 'V6-R43'),
    (5702, '调整入库重量', 5701, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:burnAdjust:edit', '#', 1, NOW(), 'V6-R43');

-- 3) 角色授权：派生自同目录「字典管理」(menu_id=105) 已有的角色（不写死 role_id），
--    再显式补超级管理员（与 V202608280200 库存月汇总同口径）。
--    ADR-0020：授权必须覆盖整条子树含父目录，否则子菜单成孤儿不显示。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, m.menu_id
FROM sys_role_menu rm
CROSS JOIN (SELECT 5700 AS menu_id UNION ALL SELECT 5701 UNION ALL SELECT 5702) m
WHERE rm.menu_id = 105;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (1, 5700), (1, 5701), (1, 5702);
