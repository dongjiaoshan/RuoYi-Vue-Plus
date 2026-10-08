-- V6 rows282–283: 出入库管理（9310）下的果蔬入库 / 果蔬出库两个工作台。
-- 9313–9316 已是猪只 / 白条工作台的按钮，本段取 9317–9321（迁移与 staging 均未占用）。
-- 菜单顺序按原型侧栏：猪只入库 → 白条分割出库 → 果蔬出库 → 果蔬入库。
SET NAMES utf8mb4;

INSERT INTO sys_menu
 (menu_id, menu_name, parent_id, order_num, path, component, query_param,
  is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
 (9317, '果蔬入库管理', 9310, 4, 'veg-in', 'djs-warehouse/inout/vegIn/index', '', 1, 1, 'C', '0', '0', 'djs:warehouse:inout:query', 'list', 1, NOW(), 'V6-R282'),
 (9318, '果蔬出库管理', 9310, 3, 'veg-out', 'djs-warehouse/inout/vegOut/index', '', 1, 1, 'C', '0', '0', 'djs:warehouse:inout:query', 'list', 1, NOW(), 'V6-R283'),
 (9319, '果蔬入库录入', 9317, 1, '', '', '', 1, 1, 'F', '0', '0', 'djs:warehouse:inout:submit', '#', 1, NOW(), 'V6-R282'),
 (9320, '果蔬处理完成', 9317, 2, '', '', '', 1, 1, 'F', '0', '0', 'djs:warehouse:inout:finish', '#', 1, NOW(), 'V6-R282'),
 (9321, '果蔬出库录入', 9318, 1, '', '', '', 1, 1, 'F', '0', '0', 'djs:warehouse:inout:submit', '#', 1, NOW(), 'V6-R283')
ON DUPLICATE KEY UPDATE
 menu_name=VALUES(menu_name), parent_id=VALUES(parent_id), order_num=VALUES(order_num),
 path=VALUES(path), component=VALUES(component), menu_type=VALUES(menu_type),
 visible=VALUES(visible), status=VALUES(status), perms=VALUES(perms), icon=VALUES(icon);

-- 沿用出入库管理已有的角色边界：拿到 9310 的角色才拿到这两个工作台，不扩到其他角色。
INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT rm.role_id, m.menu_id
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id=rm.role_id AND r.del_flag='0'
CROSS JOIN sys_menu m
WHERE rm.menu_id=9310 AND m.menu_id BETWEEN 9317 AND 9321;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id BETWEEN 9317 AND 9321;
