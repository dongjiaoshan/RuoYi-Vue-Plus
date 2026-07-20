-- ============================================================
-- ADMIN-MENU-IA-001  全模块菜单 IA 分组重构  纯 menu seed
-- ============================================================
-- 范围：仓库 9000 / 种植 8000 / 门店 10000 三域左侧菜单扁平化修复。
--       只动 sys_menu.parent_id / order_num + 新增 M 型分组父节点
--       + 给新分组父绑 role_menu 白名单。
-- 禁止：改 component / path / perms / menu_name；删任何 menu；动子菜单已有 role_menu。
-- 权威源：doc/_alignment/admin-proto-alignment-2026-06-03.md
--         §2 跨模块 IA 扁平化 + 各模块「页面架构/菜单 IA」段。
-- 分组父 menu_id 落本域预留段（撞号实测 0 命中）：
--   种植 8005-8008 / 仓库 9300-9304 / 门店 10600-10601
-- M 分组父规范：menu_type='M' / component=NULL / perms='' / is_frame=1
--               / path 唯一短串 / icon = ruoyi 内置图标。
-- role_menu 白名单：role_id NOT IN (1,101) AND del_flag='0'（实测 11 角色命中）
--                   + 超管 role_id=1 全绑 = 每父 12 行。
-- ADR-0006 菜单分治 / CLAUDE.md §6 menu_id 分段表。
-- ============================================================

-- ============================================================
-- 步骤 1 — 新增全部 M 分组父节点（11 个）
-- ============================================================

-- 仓库 9000 子 5 个分组父（对齐报告 §仓库菜单 IA line 363-372 + S1 line 434-438）
-- 种植 8000 子 4 个分组父（对齐报告 §种植菜单 IA line 518-530 + 建议 line 588）
-- 门店 10000 子 2 个分组父（对齐报告 §门店菜单 IA line 609-640 + 建议 line 716）
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- ---- 仓库分组父 9300-9304 ----
    (9300, '生产管理', 9000, 10, 'wh-production', NULL, '',
     1, 0, 'M', '0', '0', '', 'build', 1, NOW(), 'ADMIN-MENU-IA-001'),
    (9301, '产品生产管理', 9000, 11, 'wh-product-prod', NULL, '',
     1, 0, 'M', '0', '0', '', 'shopping', 1, NOW(), 'ADMIN-MENU-IA-001'),
    (9302, '库存管理', 9000, 12, 'wh-stock-mgmt', NULL, '',
     1, 0, 'M', '0', '0', '', 'druid', 1, NOW(), 'ADMIN-MENU-IA-001'),
    (9303, '仓库配置管理', 9000, 13, 'wh-config', NULL, '',
     1, 0, 'M', '0', '0', '', 'tool', 1, NOW(), 'ADMIN-MENU-IA-001'),
    (9304, '发货管理', 9000, 14, 'wh-shipment', NULL, '',
     1, 0, 'M', '0', '0', '', 'guide', 1, NOW(), 'ADMIN-MENU-IA-001'),

    -- ---- 种植分组父 8005-8008 ----
    (8005, '种植信息管理', 8000, 1, 'plt-info', NULL, '',
     1, 0, 'M', '0', '0', '', 'documentation', 1, NOW(), 'ADMIN-MENU-IA-001'),
    (8006, '种植管理', 8000, 2, 'plt-plan-mgmt', NULL, '',
     1, 0, 'M', '0', '0', '', 'tree', 1, NOW(), 'ADMIN-MENU-IA-001'),
    (8007, '农事管理', 8000, 3, 'plt-farm-work', NULL, '',
     1, 0, 'M', '0', '0', '', 'edit', 1, NOW(), 'ADMIN-MENU-IA-001'),
    (8008, '人员管理', 8000, 4, 'plt-staff', NULL, '',
     1, 0, 'M', '0', '0', '', 'peoples', 1, NOW(), 'ADMIN-MENU-IA-001'),

    -- ---- 门店分组父 10600-10601 ----
    (10600, '门店管理', 10000, 3, 'store-mgmt', NULL, '',
     1, 0, 'M', '0', '0', '', 'shopping', 1, NOW(), 'ADMIN-MENU-IA-001'),
    (10601, '退回管理', 10000, 5, 'store-return-mgmt', NULL, '',
     1, 0, 'M', '0', '0', '', 'redo', 1, NOW(), 'ADMIN-MENU-IA-001');

-- ============================================================
-- 步骤 2 — 仓库 9000 子菜单重挂（对齐报告 line 363-372 / S1 line 434-438）
-- ============================================================

-- 2-1 生产管理 9300：燎毛 / 分割 / 毛菜 / 打包工序 / 生产记录（原 parent 9000 → 9300）
UPDATE sys_menu SET parent_id=9300, order_num=1 WHERE menu_id=9070;  -- 燎毛记录
UPDATE sys_menu SET parent_id=9300, order_num=2 WHERE menu_id=9076;  -- 分割记录
UPDATE sys_menu SET parent_id=9300, order_num=3 WHERE menu_id=9090;  -- 毛菜处理
UPDATE sys_menu SET parent_id=9300, order_num=4 WHERE menu_id=9220;  -- 打包工序(M)
UPDATE sys_menu SET parent_id=9300, order_num=5 WHERE menu_id=9230;  -- 生产记录

-- 2-2 产品生产管理 9301：采购入库（外购猪只/产品生产，对齐报告"产品生产管理"组，原 parent 9000 → 9301）
UPDATE sys_menu SET parent_id=9301, order_num=1 WHERE menu_id=9060;  -- 采购入库

-- 2-3 库存管理 9302：库存查询/流水/入出库记录/包材库/盘点/盘点录入/包材领用（原 parent 9000 → 9302）
UPDATE sys_menu SET parent_id=9302, order_num=1 WHERE menu_id=9020;  -- 库存查询
UPDATE sys_menu SET parent_id=9302, order_num=2 WHERE menu_id=9110;  -- 出入库流水
UPDATE sys_menu SET parent_id=9302, order_num=3 WHERE menu_id=9240;  -- 入库记录
UPDATE sys_menu SET parent_id=9302, order_num=4 WHERE menu_id=9241;  -- 出库记录
UPDATE sys_menu SET parent_id=9302, order_num=5 WHERE menu_id=9246;  -- 包材库(M)
UPDATE sys_menu SET parent_id=9302, order_num=6 WHERE menu_id=9250;  -- 库存盘点
UPDATE sys_menu SET parent_id=9302, order_num=7 WHERE menu_id=9260;  -- 盘点录入(M)
UPDATE sys_menu SET parent_id=9302, order_num=8 WHERE menu_id=9100;  -- 包材领用

-- 2-4 仓库配置管理 9303：库位管理 / 商品主数据（原 parent 9000 → 9303）
UPDATE sys_menu SET parent_id=9303, order_num=1 WHERE menu_id=9010;  -- 库位管理
UPDATE sys_menu SET parent_id=9303, order_num=2 WHERE menu_id=9030;  -- 商品主数据

-- 2-5 发货管理 9304：发货月台 / 退货管理 / 发货流水 / 需求调度（原 parent 9000 → 9304）
UPDATE sys_menu SET parent_id=9304, order_num=1 WHERE menu_id=9200;  -- 发货月台(M)
UPDATE sys_menu SET parent_id=9304, order_num=2 WHERE menu_id=9210;  -- 退货管理
UPDATE sys_menu SET parent_id=9304, order_num=3 WHERE menu_id=9217;  -- 发货流水
UPDATE sys_menu SET parent_id=9304, order_num=4 WHERE menu_id=9270;  -- 需求调度(M)

-- 2-6 需求管理 9040(已 M 容器，对齐报告 line 400) 理 order_num=1；仓库看板 9400 着陆页置顶 order_num=0(对齐报告 line 402)
UPDATE sys_menu SET order_num=1 WHERE menu_id=9040;  -- 需求管理(M) 直挂 9000
UPDATE sys_menu SET order_num=0 WHERE menu_id=9400;  -- 仓库看板 着陆页

-- ============================================================
-- 步骤 3 — 种植 8000 子菜单重挂（对齐报告 line 518-530 / 建议 line 588）
-- ============================================================

-- 3-1 种植信息管理 8005：片区与地块/作物管理/土地有机认证/果蔬有机认证（原 parent 8000 → 8005）
UPDATE sys_menu SET parent_id=8005, order_num=1 WHERE menu_id=8010;  -- 片区与地块
UPDATE sys_menu SET parent_id=8005, order_num=2 WHERE menu_id=8020;  -- 作物管理
UPDATE sys_menu SET parent_id=8005, order_num=3 WHERE menu_id=8050;  -- 土地有机认证
UPDATE sys_menu SET parent_id=8005, order_num=4 WHERE menu_id=8060;  -- 果蔬有机认证

-- 3-2 种植管理 8006：种植计划 / 计划新建向导 / 计划详情（原 parent 8000 → 8006）
UPDATE sys_menu SET parent_id=8006, order_num=1 WHERE menu_id=8070;  -- 种植计划
UPDATE sys_menu SET parent_id=8006, order_num=2 WHERE menu_id=8075;  -- 计划新建向导
UPDATE sys_menu SET parent_id=8006, order_num=3 WHERE menu_id=8078;  -- 计划详情

-- 3-3 农事管理 8007：农事录入(已 M) / 灾害记录（原 parent 8000 → 8007）
UPDATE sys_menu SET parent_id=8007, order_num=1 WHERE menu_id=8090;  -- 农事录入(M)
UPDATE sys_menu SET parent_id=8007, order_num=2 WHERE menu_id=8100;  -- 灾害记录

-- 3-4 人员管理 8008：班组管理 / 班组绩效（原 parent 8000 → 8008）
UPDATE sys_menu SET parent_id=8008, order_num=1 WHERE menu_id=8030;  -- 班组管理
UPDATE sys_menu SET parent_id=8008, order_num=2 WHERE menu_id=8200;  -- 班组绩效

-- 3-5 采摘计划 8081(已 M，原型"采摘管理"组，本 ticket 不新建采摘父) 理 order_num；种植看板 8110 着陆页置顶；
--     种植采收操作 8500(mp M，对齐报告无明确归属) 留 8000(reports 记)
UPDATE sys_menu SET order_num=5 WHERE menu_id=8081;  -- 采摘计划(M) 直挂 8000
UPDATE sys_menu SET order_num=0 WHERE menu_id=8110;  -- 种植看板 着陆页
UPDATE sys_menu SET order_num=6 WHERE menu_id=8500;  -- 种植采收操作(mp M) 留 8000

-- ============================================================
-- 步骤 4 — 门店 10000 子菜单重挂（对齐报告 line 609-640 / 建议 line 716）
-- ============================================================

-- 4-1 门店管理 10600：门店盘点 / 门店分割 / 会员档案（原 parent 10000 → 10600）
UPDATE sys_menu SET parent_id=10600, order_num=1 WHERE menu_id=10200;  -- 门店盘点
UPDATE sys_menu SET parent_id=10600, order_num=2 WHERE menu_id=10300;  -- 门店分割
UPDATE sys_menu SET parent_id=10600, order_num=3 WHERE menu_id=10320;  -- 会员档案

-- 4-2 退回管理 10601：门店退回（原 parent 10000 → 10601）
UPDATE sys_menu SET parent_id=10601, order_num=1 WHERE menu_id=10350;  -- 门店退回

-- 4-3 门店需求 10001 / 门店经营 10100(均已 M) 理 order；门店看板 10400 着陆页置顶
--     追溯 10500 当前 parent=0 一级 M(对齐报告 line 624 保持一级) → 不动
UPDATE sys_menu SET order_num=1 WHERE menu_id=10001;  -- 门店需求(M)
UPDATE sys_menu SET order_num=2 WHERE menu_id=10100;  -- 门店经营(M)
UPDATE sys_menu SET order_num=0 WHERE menu_id=10400;  -- 门店看板 着陆页

-- ============================================================
-- 步骤 5 — 给全部 11 个新分组父绑 role_menu 白名单（抄 V202606141200:50-62 范式）
-- ============================================================

-- 白名单：role_id NOT IN (1,101) AND del_flag='0'（实测 11 角色命中）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id IN (9300,9301,9302,9303,9304, 8005,8006,8007,8008, 10600,10601);

-- 超级管理员角色 1 全绑
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id IN (9300,9301,9302,9303,9304, 8005,8006,8007,8008, 10600,10601);
