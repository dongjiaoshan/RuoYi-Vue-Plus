-- 库存月汇总菜单（admin row190：在「库存日汇总」上面新增）
-- 号段：仓库域 9000-9999，库存管理 9302 下取 9120 / 9121 / 9122。
--   这三个号从未被使用过，且紧邻 9123 库存日汇总，语义最贴。
--   ⚠️ 不复用 9124/9125/9127/9128 —— 它们是「曾建后删」的 F 行（V202607261400 删过），
--     sys_role_menu 里可能残留历史绑定，复用会让新菜单继承到意外授权。
--
-- 实现是 compute-on-read（回放 stock_flow，与日汇总同构），无新表、无跑批。

-- 1) 腾位：库存管理下 order_num >= 2 的整体后移一位，让月汇总插到日汇总(2)之前
UPDATE sys_menu SET order_num = order_num + 1 WHERE parent_id = 9302 AND order_num >= 2;

-- 2) 菜单 seed
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9120, '库存月汇总', 9302, 2, 'stockMonthly', 'djs-warehouse/stockMonthly/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:stockMonthly:list', 'chart', 1, NOW(), 'ADMIN-R190'),
    (9121, '月汇总详情', 9120, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:stockMonthly:query', '#', 1, NOW(), 'ADMIN-R190'),
    (9122, '月汇总导出', 9120, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:stockMonthly:export', '#', 1, NOW(), 'ADMIN-R190');

-- 3) 角色授权：业务角色白名单 + 超管（与库存日汇总 V202607251500 同口径）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 9120 AS menu_id UNION ALL SELECT 9121 UNION ALL SELECT 9122) m
WHERE r.del_flag = '0' AND r.role_id NOT IN (1, 101);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (1, 9120), (1, 9121), (1, 9122);
