-- V6-R154 / R155 / R156 出入库月汇总
--
-- 甲方 2026-08-31：
--   row154 在【库存管理】-【库存月汇总】下面新增【出入库月汇总】菜单；列表 = 汇总月份 + 操作
--          （操作里为「入库汇总」「出库汇总」两个下钻）
--   row155 入库汇总：按 产品 × 入库方式 × 供应商 汇总当月入库量（供应商为空的统计到一起），含导出
--   row156 出库汇总：按 产品 × 出库去向 汇总当月出库量，含导出
--
-- 实现是 compute-on-read：按月实时 GROUP BY 既有 t_warehouse_stock_flow
--   （+ JOIN t_warehouse_product_info / t_md_supplier），与兄弟页「库存月汇总」同做法。
--   ⇒ 本迁移不建任何业务表、不建字典、不加跑批，只做 sys_menu + sys_role_menu。
--   入库方式复用字典 djs_flow_type，出库去向复用 djs_stock_out_dest，产品类型复用 djs_product_type，
--   三个字典均已 seed，一个新字典项都不加。
--
-- 号段：仓库域 9000-9999，库存管理（parent 9302）下取 9145 / 9146 / 9147 / 9148。
--   这四个号在 sys_menu 与 migration 目录均零占用，不是「曾建后删」的 F 行，
--   不会从 sys_role_menu 继承历史授权。
--
-- 下钻形态：入库汇总 / 出库汇总是弹窗（el-dialog）内的完整列表，不占独立 C 菜单，
--   故只给 9146（两个下钻共用查询权限）+ 9147 / 9148（两个导出各一个权限位）三个 F 行。
--
-- 生效：/getRouters 实时读库，重新登录即生效；sys_menu 不进 redis 字典缓存，无需 flush。
-- 幂等：sys_menu 走 ON DUPLICATE KEY UPDATE，sys_role_menu 走 INSERT IGNORE，可重复执行。

SET NAMES utf8mb4;

-- 1) 腾位：库存月汇总(9120) order_num=2，新菜单排它下面 → order_num=3，原 >=3 的整体后移一位
UPDATE sys_menu SET order_num = order_num + 1 WHERE parent_id = 9302 AND order_num >= 3;

-- 2) 菜单 seed
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9145, '出入库月汇总', 9302, 3, 'inoutMonthly', 'djs-warehouse/inoutMonthly/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:inoutMonthly:list', 'chart', 1, NOW(), 'V6-R154'),
    (9146, '出入库汇总详情', 9145, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:inoutMonthly:query', '#', 1, NOW(), 'V6-R155/R156 入库汇总 + 出库汇总下钻'),
    (9147, '入库汇总导出', 9145, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:inoutMonthly:inExport', '#', 1, NOW(), 'V6-R155'),
    (9148, '出库汇总导出', 9145, 3, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:inoutMonthly:outExport', '#', 1, NOW(), 'V6-R156')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path      = VALUES(path),
    component = VALUES(component),
    menu_type = VALUES(menu_type),
    visible   = VALUES(visible),
    status    = VALUES(status),
    perms     = VALUES(perms),
    icon      = VALUES(icon);

-- 3) 角色授权：业务角色白名单 + 超管（与库存月汇总 V202608280200 / V202608280400 同口径）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 9145 AS menu_id UNION ALL SELECT 9146 UNION ALL SELECT 9147 UNION ALL SELECT 9148) m
WHERE r.del_flag = '0' AND r.role_id NOT IN (1, 101);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 9145), (1, 9146), (1, 9147), (1, 9148),
    (101, 9145), (101, 9146), (101, 9147), (101, 9148);

-- 验收 query
--   SELECT menu_id, menu_name, parent_id, order_num, path, menu_type, perms
--     FROM sys_menu WHERE menu_id BETWEEN 9145 AND 9148 ORDER BY menu_id;
--   SELECT menu_id, GROUP_CONCAT(role_id ORDER BY role_id) FROM sys_role_menu
--     WHERE menu_id BETWEEN 9145 AND 9148 GROUP BY menu_id;
