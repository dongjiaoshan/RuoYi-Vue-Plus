-- ============================================================
-- STORE-PRODUCT-PERMS-001  门店产品管理补「新增/修改/启停」写权限
-- ============================================================
-- 原型「门店管理 → 产品管理」含 新增产品 + 修改 + 启用/禁用（doc：门店产品 = 仓库产品主数据）。
-- STORE-LEDGER-001 建的 10210 产品管理菜单只给了 djs:warehouse:product:list（只读）。
-- 本迁移补 2 个 F 权限按钮（新增 / 修改，复用仓库产品写权限串），挂 10210 下并绑业务角色，
-- 使门店角色也能看到新增/修改/启停按钮（启停用 edit 权限）。超管本就全通配。
-- ============================================================
SET NAMES utf8mb4;

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (10211, '产品新增', 10210, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:product:add', '#', 1, NOW(), 'STORE-PRODUCT-PERMS-001 门店产品新增'),
    (10212, '产品修改', 10210, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:product:edit', '#', 1, NOW(), 'STORE-PRODUCT-PERMS-001 门店产品修改/启停');

-- role_menu 白名单（业务角色 + 超管，抄 STORE-RETURN-REALIGN-001 范式）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id IN (10211, 10212);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id FROM sys_menu m WHERE m.menu_id IN (10211, 10212);
