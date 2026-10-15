-- V6 row293: 果蔬出库管理（9318）下的「出库完成」按钮。剩余库存结转损耗是出库侧动作，
-- 权限独立于果蔬入库的「处理完成」（9320），只拿到出库工作台的岗位也能用。
SET NAMES utf8mb4;

INSERT INTO sys_menu
 (menu_id, menu_name, parent_id, order_num, path, component, query_param,
  is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
 (9322, '果蔬出库完成', 9318, 2, '', '', '', 1, 1, 'F', '0', '0', 'djs:warehouse:inout:outFinish', '#', 1, NOW(), 'V6-R293')
ON DUPLICATE KEY UPDATE
 menu_name=VALUES(menu_name), parent_id=VALUES(parent_id), order_num=VALUES(order_num),
 menu_type=VALUES(menu_type), visible=VALUES(visible), status=VALUES(status), perms=VALUES(perms);

-- 能做果蔬出库录入（9321）的角色同时拿到出库完成；只有页面查看权（9318）的角色不授。
INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT rm.role_id, 9322
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id=rm.role_id AND r.del_flag='0'
WHERE rm.menu_id=9321;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) VALUES (1, 9322);
