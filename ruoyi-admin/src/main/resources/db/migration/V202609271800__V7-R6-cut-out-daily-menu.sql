-- V7-R6 猪肉日分割出库统计
--
-- 甲方：在【库存管理】里增加【猪肉日分割出库统计】菜单；搜索 = 统计日期区间（默认当天）+ 出库去向 + 出库门店；
--       列表 = 统计日期 / 出库去向 / 出库门店 / 出库总重量 / 操作「查看」；
--       查看 = 右侧抽屉，列 产品编码 / 产品名称 / 出库量 / 单位 / 猪只耳号 / 出库时间，支持一键导出。
--
-- 实现是 compute-on-read：按天实时聚合既有的白条分割出库流水（t_warehouse_stock_flow）与
--   门店需求产出记录（t_warehouse_product_production），与兄弟页「出入库统计」同做法。
--   ⇒ 本迁移不建任何业务表、不建字典、不加跑批，只做 sys_menu + sys_role_menu。
--   出库去向复用字典 djs_stock_out_dest；「门店需求」是前后端约定值 store_demand，不进字典。
--
-- 号段：仓库域 9000-9999，库存管理（parent 9302）下取 9330 / 9331 / 9332。
--   这三个号在 sys_menu 与 migration 目录均零占用，不是「曾建后删」的 F 行，
--   不会从 sys_role_menu 继承历史授权。
--
-- 下钻形态：「查看」是页内右侧抽屉（el-drawer），不占独立 C 菜单，
--   故只给 9331（明细查询）+ 9332（明细导出）两个 F 行。
--
-- 生效：/getRouters 实时读库，重新登录即生效；sys_menu 不进 redis 字典缓存，无需 flush。
-- 幂等：腾位只在 9330 不存在时执行，sys_menu 走 ON DUPLICATE KEY UPDATE，sys_role_menu 走 INSERT IGNORE，可重复执行。

SET NAMES utf8mb4;

-- 1) 腾位：出入库统计(9150) order_num=4，新菜单排它下面 → order_num=5，原 >=5 的整体后移一位。
--    只在 9330 尚不存在时挪（派生表绕开 MySQL 同表子查询限制），重放不会二次后移。
UPDATE sys_menu SET order_num = order_num + 1
WHERE parent_id = 9302 AND order_num >= 5
  AND (SELECT n FROM (SELECT COUNT(*) AS n FROM sys_menu WHERE menu_id = 9330) existing) = 0;

-- 2) 菜单 seed
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9330, '猪肉日分割出库统计', 9302, 5, 'cutOutDaily', 'djs-warehouse/cutOutDaily/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:cutOutDaily:list', 'chart', 1, NOW(), 'V7-R6'),
    (9331, '分割出库明细查询', 9330, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:cutOutDaily:query', '#', 1, NOW(), 'V7-R6 查看抽屉'),
    (9332, '分割出库明细导出', 9330, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:cutOutDaily:export', '#', 1, NOW(), 'V7-R6 一键导出')
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

-- 3) 角色授权：业务角色白名单 + 超管（与出入库月汇总 V202609010600 同口径）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 9330 AS menu_id UNION ALL SELECT 9331 UNION ALL SELECT 9332) m
WHERE r.del_flag = '0' AND r.role_id NOT IN (1, 101);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 9330), (1, 9331), (1, 9332),
    (101, 9330), (101, 9331), (101, 9332);

-- 验收 query
--   SELECT menu_id, menu_name, parent_id, order_num, path, menu_type, perms
--     FROM sys_menu WHERE parent_id = 9302 OR menu_id BETWEEN 9330 AND 9332 ORDER BY parent_id, order_num;
--   SELECT menu_id, GROUP_CONCAT(role_id ORDER BY role_id) FROM sys_role_menu
--     WHERE menu_id BETWEEN 9330 AND 9332 GROUP BY menu_id;
