-- V6-R167 出入库统计
--
-- 甲方 row167：
--   1) 在【库存管理】-【出入库月汇总】下面新增【出入库统计】
--   2) 顶部两个 Tab：入库统计 / 出库统计
--   3) 入库统计：日期区间（默认近一个月）+ 产品名称模糊 + 入库方式多选 + 产品类型 + 供应商；
--      列表 = 产品名称 / 产品类型 / 规格 / 入库方式 / 入库量 / 单位 / 供应商；
--      按「产品名称 + 入库方式 + 供应商」汇总，供应商为空的统计到一起
--   4) 出库统计：日期区间（默认近一个月）+ 产品名称模糊 + 出库去向多选 + 产品类型；
--      列表 = 产品名称 / 产品类型 / 规格 / 出库去向 / 出库量 / 单位；按「产品名称 + 出库去向」汇总
--   5) 两个 Tab 都能导出对应列表数据
--
-- 实现是 compute-on-read：按日期区间实时 GROUP BY 既有 t_warehouse_stock_flow
--   （+ JOIN t_warehouse_product_info / t_md_supplier），与兄弟页「出入库月汇总」同一套聚合口径。
--   ⇒ 本迁移不建任何业务表、不建字典、不加跑批，只做 sys_menu + sys_role_menu。
--   入库方式复用字典 djs_flow_type，出库去向复用 djs_stock_out_dest，产品类型复用 djs_product_type，
--   三个字典均已 seed，一个新字典项都不加。
--
-- 与「出入库月汇总」(9145) 的关系：甲方写的是「新增」，故旧页原样保留，本页是独立新菜单。
--
-- 号段：仓库域 9000-9999，库存管理（parent 9302）下取 9150 / 9151（9145-9148 已被出入库月汇总占）。
--   这两个号在 sys_menu 与 migration 目录均零占用，不会从 sys_role_menu 继承历史授权。
--
-- 导出权限只发一个 djs:warehouse:inoutStat:export（两个 Tab 共用，走 BizTable 内置导出按钮）：
--   甲方要的是「两个 Tab 都能导出」，不是「分开授权」，拆两个权限位只会让角色配置多一步且必然一起勾。
--
-- 生效：/getRouters 实时读库，重新登录即生效；sys_menu 不进 redis 字典缓存，无需 flush。
-- 幂等：sys_menu 走 ON DUPLICATE KEY UPDATE，sys_role_menu 走 INSERT IGNORE，可重复执行。

SET NAMES utf8mb4;

-- 1) 腾位：出入库月汇总(9145) order_num=3，新菜单排它下面 → order_num=4，原 >=4 的整体后移一位
UPDATE sys_menu SET order_num = order_num + 1 WHERE parent_id = 9302 AND order_num >= 4;

-- 2) 菜单 seed
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9150, '出入库统计', 9302, 4, 'inoutStat', 'djs-warehouse/inoutStat/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:inoutStat:list', 'chart', 1, NOW(), 'V6-R167'),
    (9151, '出入库统计导出', 9150, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:inoutStat:export', '#', 1, NOW(), 'V6-R167 入库统计 + 出库统计共用')
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
CROSS JOIN (SELECT 9150 AS menu_id UNION ALL SELECT 9151) m
WHERE r.del_flag = '0' AND r.role_id NOT IN (1, 101);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 9150), (1, 9151),
    (101, 9150), (101, 9151);

-- 验收 query
--   SELECT menu_id, menu_name, parent_id, order_num, path, menu_type, perms
--     FROM sys_menu WHERE menu_id IN (9150, 9151) ORDER BY menu_id;
--   SELECT menu_id, GROUP_CONCAT(role_id ORDER BY role_id) FROM sys_role_menu
--     WHERE menu_id IN (9150, 9151) GROUP BY menu_id;
