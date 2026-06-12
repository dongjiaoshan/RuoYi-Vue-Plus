-- ============================================================
-- DJS-FIX-WMS-RALN  仓库 admin × 原型 IA 重新对齐  纯 menu seed
-- ============================================================
-- 范围（D-FIX-WMS-RALN-A）：只动 sys_menu（reparent / rename / visible / 新增 1 个 C 菜单 +
--   4 个 F 按钮）+ sys_role_menu 白名单。不改 component / path / perms（除 9040 需求由 M 目录
--   收敛为单页 C，明确改 path/component；9028 为新建页）。不删任何 menu、不改业务表。
-- 权威源：doc/_alignment/warehouse-admin-realign-2026-06-12.md（§1 目标 IA 14 叶子 + §2 批次 A）。
-- 分组父基线：V202606171100__ADMIN-MENU-IA-001 已建 9300 生产管理 / 9301 产品生产管理 /
--   9302 库存管理 / 9303 仓库配置管理 / 9304 发货管理。本轮把叶子收敛到原型 IA：
--     库位总览 9026 / 退货 9210 → 库存管理 9302；产品生产 9230 → 产品生产管理 9301。
-- menu_id 分段（CLAUDE.md §6.6 仓库 9000-9999）：外购猪只 C=9028（库位段内空号，实测 FREE）；
--   外购猪只 F 按钮 9290-9293（9290-9299 段内连续空号，实测 FREE）。
-- 幂等：reparent / rename / visible 全 UPDATE（重复跑等价）；新 menu / role_menu 用 INSERT IGNORE。
-- sys_menu / sys_role_menu 为 ruoyi 框架表，无 tenant_id（多租户走 sys_role_menu），不加 tenant_id。
-- role_menu 白名单范式：role_id NOT IN (1,101) AND del_flag='0' + 超管 role_id=1 全绑
--   （抄 V202606171100:150-162）。
-- ruoyi visible：'0'=显示 / '1'=隐藏。
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- A. reparent（叶子收敛到原型 IA 分组父）
-- ============================================================
UPDATE sys_menu SET parent_id = 9302, order_num = 2  WHERE menu_id = 9026;  -- 库位总览  → 库存管理 9302
UPDATE sys_menu SET parent_id = 9302, order_num = 6  WHERE menu_id = 9210;  -- 退货记录  → 库存管理 9302
UPDATE sys_menu SET parent_id = 9301, order_num = 1  WHERE menu_id = 9230;  -- 产品生产  → 产品生产管理 9301

-- ============================================================
-- B. rename（命名逐字对齐原型 14 叶子）
-- ============================================================
UPDATE sys_menu SET menu_name = '库位总览'     WHERE menu_id = 9026;  -- 原「各库位情况」
UPDATE sys_menu SET menu_name = '盘点记录'     WHERE menu_id = 9250;  -- 原「库存盘点」
UPDATE sys_menu SET menu_name = '退货记录'     WHERE menu_id = 9210;  -- 原「退货管理」
UPDATE sys_menu SET menu_name = '产品生产'     WHERE menu_id = 9230;  -- 原「生产记录」
UPDATE sys_menu SET menu_name = '仓库总览'     WHERE menu_id = 9400;  -- 原「仓库看板」
UPDATE sys_menu SET menu_name = '库位配置管理' WHERE menu_id = 9010;  -- 原「库位管理」

-- ============================================================
-- C. 隐藏「原型未画」现有页（visible='1'，代码/路由/后端不动，可随时 un-hide）
--    出入库流水9110 · 包材领用9100 · 采购入库9060 · 发货月台9200 · 发货流水9217
--    燎毛9070 · 分割9076 · 毛菜9090 · 礼盒配置9038
-- ============================================================
UPDATE sys_menu SET visible = '1' WHERE menu_id IN (
    9110,  -- 出入库流水（原型库存管理只分 入库/出库 记录，无合并流水）
    9100,  -- 包材领用（物资域，原型仓库未画）
    9060,  -- 采购入库（物资采购，原型仓库未画）
    9200,  -- 发货月台（原型仓库无发货管理组，发货=门店/mp 联动）
    9217,  -- 发货流水（同上）
    9070,  -- 燎毛记录（原型生产管理只画 5 车间录入，无此只读台账）
    9076,  -- 分割记录（同上）
    9090,  -- 毛菜处理（同上）
    9038   -- 礼盒配置（产品 3 入口收敛为 2：产品 9036 / 商品 9037）
);

-- ============================================================
-- D. 外购猪只 新页（原型有 admin 录入，挂产品生产管理 9301）
--    9028 列表 C 菜单 + 9290-9293 四个 F 按钮（query/add/remove/export）
-- ============================================================
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9028, '外购猪只', 9301, 2, 'outsourcePig', 'djs-warehouse/outsourcePig/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:outsourcePig:list', 'pig', 1, NOW(), 'DJS-FIX-WMS-RALN'),
    (9290, '外购猪只查询', 9028, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:outsourcePig:query',  '#', 1, NOW(), 'DJS-FIX-WMS-RALN'),
    (9291, '外购猪只新增', 9028, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:outsourcePig:add',    '#', 1, NOW(), 'DJS-FIX-WMS-RALN'),
    (9292, '外购猪只删除', 9028, 3, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:outsourcePig:remove', '#', 1, NOW(), 'DJS-FIX-WMS-RALN'),
    (9293, '外购猪只导出', 9028, 4, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:outsourcePig:export', '#', 1, NOW(), 'DJS-FIX-WMS-RALN');

-- D-role：外购猪只 9028 + 4 F 按钮 绑业务角色白名单（与仓库其余菜单同范围）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id IN (9028, 9290, 9291, 9292, 9293);

-- 超级管理员角色 1 全绑
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id IN (9028, 9290, 9291, 9292, 9293);

-- ============================================================
-- E. 需求管理 4 子菜单 → 单页 1 tab（9040 由 M 目录收敛为 C 单页）
--    原 9040 menu_type='M' / component=NULL / path='demand'；改为 C 直挂单页组件。
--    隐藏 9041-9044 四业态子菜单（visible='1'，代码/路由不删，页内 7 tab 切换）。
-- ============================================================
UPDATE sys_menu
SET menu_type = 'C',
    component = 'djs-warehouse/demand/index'
WHERE menu_id = 9040;  -- path 已为 'demand'，保持不动

UPDATE sys_menu SET visible = '1' WHERE menu_id IN (
    9041,  -- 白条需求
    9042,  -- 蔬菜需求
    9043,  -- 礼盒需求
    9044   -- 其他需求
);
