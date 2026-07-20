-- ============================================================
-- STORE-LEDGER-001  门店管理 菜单：当日盘点页 10206 + 产品管理入口 10210
-- ============================================================
-- ① 当日盘点（C，10206，挂门店管理 10600）：整页可编辑大表录入经营六列流水。
--    门店盘点列表沿用 10200（component djs-store/check/index 不变，行为重建为台账列表）。
-- ② 产品管理（C，10210，挂门店管理 10600，order_num=1 排前）：门店能卖哪些产品 + 产品建档展示，
--    复用仓库产品读权限 djs:warehouse:product:list。同时把门店盘点 10200 order_num 调成 2。
-- role_menu 白名单范式：role_id NOT IN(1,101) AND del_flag='0' + 超管 role_id=1 全绑。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. sys_menu seed
-- ------------------------------------------------------------
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 产品管理（门店域入口；列表数据复用仓库产品，故权限串用 djs:warehouse:product:list）
    (10210, '产品管理', 10600, 1, 'product', 'djs-store/product/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:product:list', 'goods', 1, NOW(), 'STORE-LEDGER-001 门店产品管理入口'),
    -- 当日盘点（整页可编辑大表，挂门店管理；从门店盘点列表「新增当日盘点」进）
    (10206, '当日盘点', 10600, 9, 'check-entry', 'djs-store/check/entry/index', '',
     1, 0, 'C', '1', '0', 'djs:store:check:add', 'edit', 1, NOW(), 'STORE-LEDGER-001 当日盘点整表录入（visible 1 隐藏，从列表进）');

-- 门店盘点 10200 排到产品管理之后
UPDATE sys_menu SET order_num = 2 WHERE menu_id = 10200;

-- ------------------------------------------------------------
-- 2. role_menu 白名单绑定（业务角色 + 超管）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id IN (10206, 10210);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id FROM sys_menu m WHERE m.menu_id IN (10206, 10210);
