-- ============================================================
-- STORE-TRACE-ONSITE-001  追溯码管理对齐原型：果蔬列重排 + 猪肉现场生码（ADR-0015）
-- ============================================================
-- 原型「追溯码管理」= 果蔬追溯码管理（产出流 10 列只读 + 追溯码打印）
--   + 猪肉追溯码管理（选猪只→选部位→录重量→现场生码打印）。
-- Phase 0 已把 10500 追溯码管理(M) 迁入门店域、10501 果蔬追溯码管理(C)。
-- 本迁移：① 零售部位字典 djs_pork_cut_product；② 猪肉现场生码菜单 10520/10521；
--         ③ 果蔬子页 component 改指门店域专用页（10 列产出流）。
-- 后端写端点 /djs/store/trace/{pig/list,gen} 已落（org.dromara.djs.store.trace.*）。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 零售部位字典 djs_pork_cut_product（5 部位，对齐原型猪肉追溯码部位卡）
-- ------------------------------------------------------------
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, remark)
VALUES (920670, '1001', '猪肉零售部位', 'djs_pork_cut_product', 1, 'STORE-TRACE-ONSITE-001 现场生码部位卡');

INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, remark)
VALUES
    (92067001, '1001', 1, '前腿肉', '前腿肉', 'djs_pork_cut_product', '', 'primary', 'Y', 1, 'STORE-TRACE-ONSITE-001'),
    (92067002, '1001', 2, '五花肉', '五花肉', 'djs_pork_cut_product', '', 'primary', 'N', 1, 'STORE-TRACE-ONSITE-001'),
    (92067003, '1001', 3, '排骨',   '排骨',   'djs_pork_cut_product', '', 'primary', 'N', 1, 'STORE-TRACE-ONSITE-001'),
    (92067004, '1001', 4, '肘子',   '肘子',   'djs_pork_cut_product', '', 'primary', 'N', 1, 'STORE-TRACE-ONSITE-001'),
    (92067005, '1001', 5, '大排',   '大排',   'djs_pork_cut_product', '', 'primary', 'N', 1, 'STORE-TRACE-ONSITE-001');

-- ------------------------------------------------------------
-- 2. 果蔬追溯码管理子页 component 改指门店域专用页（10 列产出流，原型口径）
--    后端仍复用 /djs/warehouse/trace/list（TraceCodeListVo 已聚合产出流字段）。
-- ------------------------------------------------------------
UPDATE sys_menu SET component = 'djs-store/trace/veg/index' WHERE menu_id = 10501;

-- ------------------------------------------------------------
-- 3. 猪肉追溯码管理（C，10520，挂追溯码管理 10500）+ 追溯码打印（F，10521）
-- ------------------------------------------------------------
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (10520, '猪肉追溯码管理', 10500, 2, 'trace-pork', 'djs-store/trace/pork/index', '',
     1, 0, 'C', '0', '0', 'djs:store:trace:gen', 'qrcode', 1, NOW(), 'STORE-TRACE-ONSITE-001 现场生码'),
    (10521, '追溯码打印', 10520, 1, '', '', '',
     1, 0, 'F', '0', '0', 'djs:store:trace:print', '#', 1, NOW(), 'STORE-TRACE-ONSITE-001');

-- role_menu 白名单（业务角色 + 超管，抄 V202606270200 范式）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id IN (10520, 10521);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id FROM sys_menu m WHERE m.menu_id IN (10520, 10521);
