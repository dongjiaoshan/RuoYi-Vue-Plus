-- ============================================================
-- FIX-WMS-PRODSPLIT-001  产品/商品/礼盒菜单拆三入口  纯 menu seed
-- ============================================================
-- 决策：D-FIX-6 _decisions.md A3 —— 产品(type=1 自产) / 商品(type=2 外购) /
--       礼盒(type=3) 各一个独立菜单入口，均挂 9303「仓库配置管理」父菜单下，
--       用 query_param 注入 productType 预过滤，复用同一组件 djs-warehouse/product/index，
--       共用 djs:warehouse:product:* 权限，共表 t_warehouse_product_info 不动。
--       旧 9030「商品主数据」默认隐藏（visible='1'，保留路由不删）。
--
-- menu_id：9036/9037/9038（9030-9035 段内空闲号，实测 0 命中）
-- path：product-conf / goods-conf / giftbox-conf 三者互不同（同 component 多路由）
-- 不动：表 / Service / Controller / perms / 字典 / t_warehouse_gift_box / ruoyi 自带
-- sys_menu 为 ruoyi 框架表无 tenant_id（多租户走 sys_role_menu），不加 tenant_id
-- menu_id 守 9000-9999 仓库段（CLAUDE.md §6.6）
-- 授权范式抄 V202606171100__ADMIN-MENU-IA-001:150-162
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 三个产品配置 C 菜单（挂 9303 仓库配置管理；query_param 注入 productType）
--    列顺序对齐 V202605311100__WMS-MD-002:147-150
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query_param,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
VALUES
    (9036, '产品配置', 9303, 3, 'product-conf', 'djs-warehouse/product/index', '{"productType":"1"}',
     1, 0, 'C', '0', '0', 'djs:warehouse:product:list', 'shopping',
     1, NOW(), 'FIX-WMS-PRODSPLIT-001'),
    (9037, '商品配置', 9303, 4, 'goods-conf', 'djs-warehouse/product/index', '{"productType":"2"}',
     1, 0, 'C', '0', '0', 'djs:warehouse:product:list', 'goods',
     1, NOW(), 'FIX-WMS-PRODSPLIT-001'),
    (9038, '礼盒配置', 9303, 5, 'giftbox-conf', 'djs-warehouse/product/index', '{"productType":"3"}',
     1, 0, 'C', '0', '0', 'djs:warehouse:product:list', 'gift',
     1, NOW(), 'FIX-WMS-PRODSPLIT-001');

-- ------------------------------------------------------------
-- 2. 隐藏旧 9030「商品主数据」入口（保留路由 / 数据 / 子按钮权限不删）
-- ------------------------------------------------------------
UPDATE sys_menu SET visible = '1' WHERE menu_id = 9030;

-- ------------------------------------------------------------
-- 3. sys_role_menu 授权：把 9036/9037/9038 授给当前持有 9030 的全部角色
--    （继承旧入口的可见角色，保证三新入口可见）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, n.menu_id
FROM sys_role_menu rm
CROSS JOIN (SELECT 9036 AS menu_id UNION ALL SELECT 9037 UNION ALL SELECT 9038) n
WHERE rm.menu_id = 9030;

-- 超级管理员角色 1 全绑（兜底，与 9030 是否已授无关）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id IN (9036, 9037, 9038);
