-- ============================================================
-- WMS-STOCK-001 菜单 seed（9250-9269 段）
--
-- ADR-0006 菜单分治：仓库域 9000-10999；库存盘点占 9250-9269 段（避开 9200-9249 WMS-SHIP-001）
--   父菜单 9000 仓库 已由 D7 WMS-MD-001 V202605290910 seed
--
-- 结构：
--   9250 库存盘点 admin C 菜单（挂 vue 列表 djs-warehouse/check/index）
--     9251-9256 admin 端 6 perms（list/query/add/complete/cancel/export）
--   9260 盘点录入 mp 入口 M 父菜单（perms 容器，mp 工人板块通过路由触达）
--     9261-9265 mp 端 5 perms（overview/list/submit/edit/remove）
--
-- 角色绑定（D10 DJS-FIX-SYS-W22-004 白名单范式）：
--   list/query/overview 类（含 mp 入口 + admin 列表）：role_id NOT IN (1, 101) AND del_flag='0'
--   write 类（mp 提交 + admin 新建/完成/取消）：role_key IN ('boss','manager','warehouse_admin','warehouse_worker')
--   export 类：role_key IN ('boss','manager','warehouse_admin')
-- ============================================================

SET NAMES utf8mb4;

-- ============== 1. menu seed ==============
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
    -- 9250 库存盘点 admin（挂 vue 列表）
    (9250, '库存盘点', 9000, 25, 'check',        'djs-warehouse/check/index', '', 1, 0, 'C', '0', '0', 'djs:warehouse:check:list',                 'cascader',  1, NOW(), 'WMS-STOCK-001 admin'),
    (9251, '盘点查询', 9250, 1, '',              '',                          '', 1, 0, 'F', '0', '0', 'djs:warehouse:check:query',                '#',         1, NOW(), 'WMS-STOCK-001'),
    (9252, '新建盘点', 9250, 2, '',              '',                          '', 1, 0, 'F', '0', '0', 'djs:warehouse:check:add',                  '#',         1, NOW(), 'WMS-STOCK-001 新建并锁库位'),
    (9253, '完成盘点', 9250, 3, '',              '',                          '', 1, 0, 'F', '0', '0', 'djs:warehouse:check:complete',             '#',         1, NOW(), 'WMS-STOCK-001 写流水 + 回写库存 + 解锁'),
    (9254, '取消盘点', 9250, 4, '',              '',                          '', 1, 0, 'F', '0', '0', 'djs:warehouse:check:cancel',               '#',         1, NOW(), 'WMS-STOCK-001 解锁不回写'),
    (9255, '盘点导出', 9250, 5, '',              '',                          '', 1, 0, 'F', '0', '0', 'djs:warehouse:check:export',               '#',         1, NOW(), 'WMS-STOCK-001'),

    -- 9260 盘点录入 mp 入口 M 父菜单（admin 端不挂 vue，仅 perms 容器；mp 工人板块通过路由触达）
    (9260, '盘点录入', 9000, 26, 'check-applet',  NULL,                       '', 1, 0, 'M', '0', '0', '',                                         'edit',      1, NOW(), 'WMS-STOCK-001 mp 入口'),
    (9261, '多库一览 mp', 9260, 1, '',            '',                          '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:check:overview',      '#',         1, NOW(), 'WMS-STOCK-001 mp 多库一览'),
    (9262, '盘点明细 mp', 9260, 2, '',            '',                          '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:check:list',          '#',         1, NOW(), 'WMS-STOCK-001 mp 已录入 line 列表'),
    (9263, '实盘录入 mp', 9260, 3, '',            '',                          '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:check:submit',        '#',         1, NOW(), 'WMS-STOCK-001 mp 提交实盘 line'),
    (9264, '明细修改 mp', 9260, 4, '',            '',                          '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:check:edit',          '#',         1, NOW(), 'WMS-STOCK-001 mp 改实盘 line'),
    (9265, '明细删除 mp', 9260, 5, '',            '',                          '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:check:remove',        '#',         1, NOW(), 'WMS-STOCK-001 mp 删实盘 line');

-- ============== 2. role_menu 白名单绑定 ==============
-- list/query/overview 类（含 mp 入口可见性 + admin 列表 query）：全 11 业务角色
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 9250 AS menu_id UNION SELECT 9251                          -- admin 盘点目录 + 查询
    UNION SELECT 9260 UNION SELECT 9261 UNION SELECT 9262             -- mp 入口 + 多库一览 + 明细列表
) m
WHERE r.role_id NOT IN (1, 101) AND r.del_flag = '0';

-- write 类：4 角色（仓库相关 + boss/manager）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 9252 AS menu_id UNION SELECT 9253 UNION SELECT 9254        -- admin 新建/完成/取消
    UNION SELECT 9263 UNION SELECT 9264 UNION SELECT 9265             -- mp 提交/改/删 实盘 line
) m
WHERE r.role_key IN ('boss', 'manager', 'warehouse_admin', 'warehouse_worker')
  AND r.del_flag = '0';

-- export 类：3 角色（高敏，工人不下放）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 9255 AS menu_id                                            -- admin 盘点导出
) m
WHERE r.role_key IN ('boss', 'manager', 'warehouse_admin')
  AND r.del_flag = '0';
