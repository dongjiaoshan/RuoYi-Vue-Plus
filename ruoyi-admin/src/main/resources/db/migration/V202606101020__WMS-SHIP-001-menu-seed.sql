-- ============================================================
-- WMS-SHIP-001 菜单 seed（9200-9217 共 18 行 menu）
--
-- ADR-0006 菜单分治：仓库域 9000-10999；发货月台占 9200-9249 段（CLAUDE.md §6 已固化）
--   父菜单 9000 仓库 已由 D7 WMS-MD-001 V202605290910 seed
--
-- 结构：
--   9200 发货月台 M2 父菜单（仓库 9000 子）
--     9201-9206 mp 端 6 perms（4 业态 + 退货 + check 共用）
--   9210 退货管理 M2 父菜单
--     9211-9217 admin 端 7 perms（list/query/add/edit/remove/confirm/export）
--
-- 角色绑定（D10 DJS-FIX-SYS-W22-004 白名单范式）：
--   list/query 类（含 mp 入口 + admin 列表）：role_id NOT IN (1, 101) AND del_flag='0'
--   write 类（mp 提交 + admin 新增/编辑/删除/确认）：role_key IN ('boss','manager','warehouse_admin','warehouse_worker')
--   export 类：role_key IN ('boss','manager','warehouse_admin')
-- ============================================================

SET NAMES utf8mb4;

-- ============== 1. menu seed ==============
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
    -- 9200 发货月台 mp 入口 M2 父菜单（admin 端不挂 vue，仅作为 perms 容器；mp 工人板块通过路由触达，不挂 admin 路由）
    (9200, '发货月台', 9000, 20, 'shipment',       NULL,                            '', 1, 0, 'M', '0', '0', '',                                            'truck',          1, NOW(), 'WMS-SHIP-001'),
    (9201, '清点确认 mp', 9200, 1, '',             '',                              '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:ship:check',             '#',              1, NOW(), 'WMS-SHIP-001 mp 清点确认'),
    (9202, '白条发货 mp', 9200, 2, '',             '',                              '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:ship:whiteBar',          '#',              1, NOW(), 'WMS-SHIP-001 mp 白条发货'),
    (9203, '蔬菜发货 mp', 9200, 3, '',             '',                              '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:ship:vegetable',         '#',              1, NOW(), 'WMS-SHIP-001 mp 蔬菜发货'),
    (9204, '礼盒发货 mp', 9200, 4, '',             '',                              '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:ship:giftBox',           '#',              1, NOW(), 'WMS-SHIP-001 mp 礼盒发货'),
    (9205, '门店发货 mp', 9200, 5, '',             '',                              '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:ship:storeShip',         '#',              1, NOW(), 'WMS-SHIP-001 mp 其他业态门店发货'),
    (9206, '退货录入 mp', 9200, 6, '',             '',                              '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:return:add',             '#',              1, NOW(), 'WMS-SHIP-001 mp 退货录入'),

    -- 9210 退货管理 admin（挂 vue 列表）
    (9210, '退货管理', 9000, 21, 'return',          'djs-warehouse/return/index',   '', 1, 0, 'C', '0', '0', 'djs:warehouse:return:list',                   'refresh',        1, NOW(), 'WMS-SHIP-001 admin'),
    (9211, '退货查询', 9210, 1, '',                 '',                             '', 1, 0, 'F', '0', '0', 'djs:warehouse:return:query',                  '#',              1, NOW(), 'WMS-SHIP-001'),
    (9212, '退货新增', 9210, 2, '',                 '',                             '', 1, 0, 'F', '0', '0', 'djs:warehouse:return:add',                    '#',              1, NOW(), 'WMS-SHIP-001'),
    (9213, '退货修改', 9210, 3, '',                 '',                             '', 1, 0, 'F', '0', '0', 'djs:warehouse:return:edit',                   '#',              1, NOW(), 'WMS-SHIP-001'),
    (9214, '退货删除', 9210, 4, '',                 '',                             '', 1, 0, 'F', '0', '0', 'djs:warehouse:return:remove',                 '#',              1, NOW(), 'WMS-SHIP-001'),
    (9215, '退货确认', 9210, 5, '',                 '',                             '', 1, 0, 'F', '0', '0', 'djs:warehouse:return:confirm',                '#',              1, NOW(), 'WMS-SHIP-001 联动 stock_flow'),
    (9216, '退货导出', 9210, 6, '',                 '',                             '', 1, 0, 'F', '0', '0', 'djs:warehouse:return:export',                 '#',              1, NOW(), 'WMS-SHIP-001'),

    -- 9217 发货列表 admin（挂 vue 只读列表，admin 不主动录入；仅审计 / 追溯）
    (9217, '发货流水', 9000, 22, 'shipment-list',   'djs-warehouse/shipment/index', '', 1, 0, 'C', '0', '0', 'djs:warehouse:shipment:list',                 'document',       1, NOW(), 'WMS-SHIP-001 admin 只读'),
    (9218, '发货查询', 9217, 1, '',                 '',                             '', 1, 0, 'F', '0', '0', 'djs:warehouse:shipment:query',                '#',              1, NOW(), 'WMS-SHIP-001'),
    (9219, '发货导出', 9217, 2, '',                 '',                             '', 1, 0, 'F', '0', '0', 'djs:warehouse:shipment:export',               '#',              1, NOW(), 'WMS-SHIP-001');

-- ============== 2. role_menu 白名单绑定（避免硬编码 role_key 命中失败）==============
-- list/query 类（含 mp 入口可见性 + admin 列表 query）：全 11 业务角色
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 9200 AS menu_id UNION SELECT 9201 UNION SELECT 9202 UNION SELECT 9203 UNION SELECT 9204
    UNION SELECT 9205 UNION SELECT 9206                                    -- mp 入口 + 6 perms 全员可见
    UNION SELECT 9210 UNION SELECT 9211                                    -- admin 退货管理目录 + 查询
    UNION SELECT 9217 UNION SELECT 9218                                    -- admin 发货流水目录 + 查询
) m
WHERE r.role_id NOT IN (1, 101) AND r.del_flag = '0';

-- write 类：4 角色（仓库相关 + boss/manager）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 9212 AS menu_id UNION SELECT 9213 UNION SELECT 9214 UNION SELECT 9215   -- admin 退货 add/edit/remove/confirm
) m
WHERE r.role_key IN ('boss', 'manager', 'warehouse_admin', 'warehouse_worker')
  AND r.del_flag = '0';

-- export 类：3 角色（高敏，工人不下放）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 9216 AS menu_id UNION SELECT 9219     -- admin 退货导出 + 发货导出
) m
WHERE r.role_key IN ('boss', 'manager', 'warehouse_admin')
  AND r.del_flag = '0';
