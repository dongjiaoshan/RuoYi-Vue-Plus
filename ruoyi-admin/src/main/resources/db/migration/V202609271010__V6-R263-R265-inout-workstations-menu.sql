-- V6 rows263–265: production / intake & dispatch / two recording workstations.
-- IDs 9310–9316 checked unused in migrations and the staging baseline.
SET NAMES utf8mb4;

INSERT INTO sys_menu
 (menu_id, menu_name, parent_id, order_num, path, component, query_param,
  is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
 (9310, '出入库管理', 9300, 5, 'inout', NULL, '', 1, 1, 'M', '0', '0', '', 'build', 1, NOW(), 'V6-R263'),
 (9311, '猪只入库管理', 9310, 1, 'pig', 'djs-warehouse/inout/pig/index', '', 1, 1, 'C', '0', '0', 'djs:warehouse:inout:query', 'list', 1, NOW(), 'V6-R264'),
 (9312, '白条分割出库管理', 9310, 2, 'cut', 'djs-warehouse/inout/cut/index', '', 1, 1, 'C', '0', '0', 'djs:warehouse:inout:query', 'list', 1, NOW(), 'V6-R265'),
 (9313, '猪只产品录入', 9311, 1, '', '', '', 1, 1, 'F', '0', '0', 'djs:warehouse:inout:submit', '#', 1, NOW(), 'V6-R264'),
 (9314, '猪只处理完成', 9311, 2, '', '', '', 1, 1, 'F', '0', '0', 'djs:warehouse:inout:finish', '#', 1, NOW(), 'V6-R264'),
 (9315, '分割产品录入', 9312, 1, '', '', '', 1, 1, 'F', '0', '0', 'djs:warehouse:inout:submit', '#', 1, NOW(), 'V6-R265'),
 (9316, '分割完成', 9312, 2, '', '', '', 1, 1, 'F', '0', '0', 'djs:warehouse:inout:finish', '#', 1, NOW(), 'V6-R265')
ON DUPLICATE KEY UPDATE
 menu_name=VALUES(menu_name), parent_id=VALUES(parent_id), order_num=VALUES(order_num),
 path=VALUES(path), component=VALUES(component), menu_type=VALUES(menu_type),
 visible=VALUES(visible), status=VALUES(status), perms=VALUES(perms), icon=VALUES(icon);

-- Retain the existing production-role boundary. No grants to unrelated roles.
INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT rm.role_id, m.menu_id
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id=rm.role_id AND r.del_flag='0'
CROSS JOIN sys_menu m
WHERE rm.menu_id=9300 AND m.menu_id BETWEEN 9310 AND 9316;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id BETWEEN 9310 AND 9316;
