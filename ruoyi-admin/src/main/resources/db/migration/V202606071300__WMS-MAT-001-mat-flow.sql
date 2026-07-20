-- ============================================================
-- WMS-MAT-001 物资领用 / 退回 / 损耗 + stock_flow CRUD 完整治理
--
-- 1. ALTER t_warehouse_stock_flow 补齐 proof_oss_ids 字段（其他 doc/11 §2.3 字段 D7 已建）
-- 2. 字典扩 djs_belong_type 3 值 (package/feed/seed) + 新建 djs_flow_type / djs_in_type / djs_mat_type
-- 3. INSERT product_info 基础 SKU 18 行（白条 2 + 包材 5 + 采食 5 + 种子 5；白条-猪头/猪蹄）
-- 4. sys_menu seed 9100-9114
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. ALTER t_warehouse_stock_flow 补 proof_oss_ids（其他字段 D7 V202605290910 已建齐）
-- ------------------------------------------------------------
ALTER TABLE t_warehouse_stock_flow
    ADD COLUMN proof_oss_ids VARCHAR(500) NULL COMMENT '凭证图 OSS IDs CSV（WMS-MAT-001）' AFTER remark;

-- ------------------------------------------------------------
-- 2. 字典扩 + 新建
-- ------------------------------------------------------------
-- 扩 djs_belong_type 3 值（package/feed/seed），原 6 → 9（dict_code 续 1020045 之后）
INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1020046, '1001', 6, '包材',     'package', 'djs_belong_type', '', 'info', 'N', 1, NOW(), 'WMS-MAT-001'),
    (1020047, '1001', 7, '采食原料', 'feed',    'djs_belong_type', '', 'info', 'N', 1, NOW(), 'WMS-MAT-001'),
    (1020048, '1001', 8, '种子',     'seed',    'djs_belong_type', '', 'info', 'N', 1, NOW(), 'WMS-MAT-001');

-- 新建 4 个字典类型（dict_id 920500+，避开已用 920402）
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark) VALUES
    (920500, '1001', '流水类型', 'djs_flow_type', 1, NOW(), 'WMS-MAT-001 业务流水语义'),
    (920510, '1001', '入库类型', 'djs_in_type',   1, NOW(), 'WMS-MAT-001 doc/11 §2.3 R17'),
    (920520, '1001', '物资类型', 'djs_mat_type',  1, NOW(), 'WMS-MAT-001 mp 端 9 类分类');

-- 字典数据：djs_flow_type 13 / djs_in_type 2 / djs_mat_type 9
INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    -- djs_flow_type 13 值
    (102450000, '1001', 0, '领用出库',   'pick_out',       'djs_flow_type', '', 'warning', 'Y', 1, NOW(), 'WMS-MAT-001'),
    (102450001, '1001', 1, '退回入库',   'return_in',      'djs_flow_type', '', 'success', 'N', 1, NOW(), 'WMS-MAT-001'),
    (102450002, '1001', 2, '损耗',       'loss',           'djs_flow_type', '', 'danger',  'N', 1, NOW(), 'WMS-MAT-001'),
    (102450003, '1001', 3, '分割出库',   'cut_out',        'djs_flow_type', '', 'warning', 'N', 1, NOW(), 'WMS-MAT-001'),
    (102450004, '1001', 4, '分割品入库', 'cut_out_in',     'djs_flow_type', '', 'success', 'N', 1, NOW(), 'WMS-MAT-001'),
    (102450005, '1001', 5, '蔬菜入库',   'veg_stock_in',   'djs_flow_type', '', 'success', 'N', 1, NOW(), 'WMS-MAT-001'),
    (102450006, '1001', 6, '白条入库',   'bar_in_stock',   'djs_flow_type', '', 'success', 'N', 1, NOW(), 'WMS-MAT-001'),
    (102450007, '1001', 7, '燎毛出库',   'slaughter_burn', 'djs_flow_type', '', 'warning', 'N', 1, NOW(), 'WMS-MAT-001'),
    (102450008, '1001', 8, '发货出库',   'ship_out',       'djs_flow_type', '', 'warning', 'N', 1, NOW(), 'WMS-MAT-001'),
    (102450009, '1001', 9, '盘盈入库',   'check_in',       'djs_flow_type', '', 'success', 'N', 1, NOW(), 'WMS-MAT-001'),
    (102450010, '1001', 10, '盘亏出库', 'check_out',       'djs_flow_type', '', 'danger',  'N', 1, NOW(), 'WMS-MAT-001'),
    (102450011, '1001', 11, '供应商入库','supplier_in',    'djs_flow_type', '', 'primary', 'N', 1, NOW(), 'WMS-MAT-001'),
    (102450012, '1001', 12, '其他',     'other',           'djs_flow_type', '', 'info',    'N', 1, NOW(), 'WMS-MAT-001'),
    -- djs_in_type
    (102451000, '1001', 0, '自产',     '1', 'djs_in_type', '', 'primary', 'Y', 1, NOW(), 'WMS-MAT-001'),
    (102451001, '1001', 1, '供应商',   '2', 'djs_in_type', '', 'success', 'N', 1, NOW(), 'WMS-MAT-001'),
    -- djs_mat_type 9 值
    (102452000, '1001', 0, '包材',     'package',  'djs_mat_type', '', 'info',    'Y', 1, NOW(), 'WMS-MAT-001'),
    (102452001, '1001', 1, '白条',     'white_bar','djs_mat_type', '', 'warning', 'N', 1, NOW(), 'WMS-MAT-001'),
    (102452002, '1001', 2, '蔬菜',     'vegetable','djs_mat_type', '', 'success', 'N', 1, NOW(), 'WMS-MAT-001'),
    (102452003, '1001', 3, '鸡蛋',     'egg',      'djs_mat_type', '', 'info',    'N', 1, NOW(), 'WMS-MAT-001'),
    (102452004, '1001', 4, '干货',     'dry_good', 'djs_mat_type', '', 'info',    'N', 1, NOW(), 'WMS-MAT-001'),
    (102452005, '1001', 5, '果蔬',     'fruit_veg','djs_mat_type', '', 'success', 'N', 1, NOW(), 'WMS-MAT-001'),
    (102452006, '1001', 6, '采食原料', 'feed',     'djs_mat_type', '', 'info',    'N', 1, NOW(), 'WMS-MAT-001'),
    (102452007, '1001', 7, '药品',     'medicine', 'djs_mat_type', '', 'danger',  'N', 1, NOW(), 'WMS-MAT-001'),
    (102452008, '1001', 8, '种子',     'seed',     'djs_mat_type', '', 'info',    'N', 1, NOW(), 'WMS-MAT-001');

-- ------------------------------------------------------------
-- 3. INSERT product_info 基础 SKU
--    白条 2 行（PROD-WHITE-BAR-01 D08-CLOSING 已 seed，本 ticket 补 -02/-03）
--    包材 5 + 采食原料 5 + 种子 5 = 17 行新增
-- ------------------------------------------------------------
INSERT IGNORE INTO t_warehouse_product_info
    (product_id, product_name, product_type, product_unit, product_spec, belong_type, product_attr, product_status, is_delivery, create_by, create_time, remark)
VALUES
    -- 白条 2 行新增（PROD-WHITE-BAR-01 D08-CLOSING 已 seed 跳过）
    ('PROD-WHITE-BAR-02', '白条·猪头',  1, 'kg', '整',   'white_bar', 1, 0, 1, 1, NOW(), 'WMS-MAT-001 D08 #11 决策 a'),
    ('PROD-WHITE-BAR-03', '白条·猪蹄',  1, 'kg', '只',   'white_bar', 1, 0, 1, 1, NOW(), 'WMS-MAT-001 D08 #11 决策 a'),
    -- 包材 5 行（belong_type='package'，product_attr=2 原材料）
    ('PROD-PACK-BAG-01',   '塑料袋',    2, '个',  '500g/包', 'package', 2, 0, 0, 1, NOW(), 'WMS-MAT-001 seed'),
    ('PROD-PACK-BOX-01',   '纸箱',      2, '个',  '5kg/箱',  'package', 2, 0, 0, 1, NOW(), 'WMS-MAT-001 seed'),
    ('PROD-PACK-LABEL-01', '标签贴纸',  2, '张',  '-',       'package', 2, 0, 0, 1, NOW(), 'WMS-MAT-001 seed'),
    ('PROD-PACK-ROPE-01',  '捆扎绳',    2, '米',  '-',       'package', 2, 0, 0, 1, NOW(), 'WMS-MAT-001 seed'),
    ('PROD-PACK-OTHER-01', '其他包材',  2, '个',  '-',       'package', 2, 0, 0, 1, NOW(), 'WMS-MAT-001 seed'),
    -- 采食原料 5 行
    ('PROD-FEED-CORN-01',  '玉米',      2, 'kg',  '袋装',    'feed', 2, 0, 0, 1, NOW(), 'WMS-MAT-001 seed'),
    ('PROD-FEED-SOY-01',   '大豆',      2, 'kg',  '袋装',    'feed', 2, 0, 0, 1, NOW(), 'WMS-MAT-001 seed'),
    ('PROD-FEED-BRAN-01',  '麸皮',      2, 'kg',  '袋装',    'feed', 2, 0, 0, 1, NOW(), 'WMS-MAT-001 seed'),
    ('PROD-FEED-GRASS-01', '牧草',      2, 'kg',  '-',       'feed', 2, 0, 0, 1, NOW(), 'WMS-MAT-001 seed'),
    ('PROD-FEED-OTHER-01', '其他原料',  2, 'kg',  '-',       'feed', 2, 0, 0, 1, NOW(), 'WMS-MAT-001 seed'),
    -- 种子 5 行
    ('PROD-SEED-VEG-01',   '蔬菜种子',  2, 'g',   '-',       'seed', 2, 0, 0, 1, NOW(), 'WMS-MAT-001 seed'),
    ('PROD-SEED-GRAIN-01', '粮食种子',  2, 'g',   '-',       'seed', 2, 0, 0, 1, NOW(), 'WMS-MAT-001 seed'),
    ('PROD-SEED-FRUIT-01', '果树种子',  2, '粒',  '-',       'seed', 2, 0, 0, 1, NOW(), 'WMS-MAT-001 seed'),
    ('PROD-SEED-HERB-01',  '草本种子',  2, 'g',   '-',       'seed', 2, 0, 0, 1, NOW(), 'WMS-MAT-001 seed'),
    ('PROD-SEED-OTHER-01', '其他种子',  2, 'g',   '-',       'seed', 2, 0, 0, 1, NOW(), 'WMS-MAT-001 seed');

-- ------------------------------------------------------------
-- 4. sys_menu seed（9100 包材领用 + 9110 出入库流水）
-- ------------------------------------------------------------
-- 4.1 包材领用菜单（C 类，admin 只读列表）
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9100, '包材领用', 9000, 6, 'matPack', 'djs-warehouse/matPack/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:mat:pack:list', 'box', 1, NOW(), 'WMS-MAT-001 包材标杆'),
    (9101, '包材查询', 9100, 1, '', '', '', 1, 0, 'F', '0', '0', 'djs:warehouse:mat:pack:list',   '#', 1, NOW(), 'WMS-MAT-001'),
    (9105, '包材导出', 9100, 5, '', '', '', 1, 0, 'F', '0', '0', 'djs:warehouse:mat:pack:export', '#', 1, NOW(), 'WMS-MAT-001'),
    (9106, '物资领用', 9100, 6, '', '', '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:mat:pick',   '#', 1, NOW(), 'WMS-MAT-001 mp'),
    (9107, '物资退回', 9100, 7, '', '', '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:mat:return', '#', 1, NOW(), 'WMS-MAT-001 mp'),
    (9108, '物资损耗', 9100, 8, '', '', '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:mat:loss',   '#', 1, NOW(), 'WMS-MAT-001 mp');

-- 4.2 出入库流水菜单（C 类，admin 查询，预热 D11 WMS-FLOW-001）
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9110, '出入库流水', 9000, 7, 'stockFlow', 'djs-warehouse/stockFlow/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:stockFlow:list', 'tickets', 1, NOW(), 'WMS-MAT-001 流水查询 / D11 扩展'),
    (9111, '流水查询', 9110, 1, '', '', '', 1, 0, 'F', '0', '0', 'djs:warehouse:stockFlow:list',   '#', 1, NOW(), 'WMS-MAT-001'),
    (9112, '流水调账', 9110, 2, '', '', '', 1, 0, 'F', '0', '0', 'djs:warehouse:stockFlow:adjust', '#', 1, NOW(), 'V1 placeholder D11 WMS-FLOW-001'),
    (9114, '流水导出', 9110, 5, '', '', '', 1, 0, 'F', '0', '0', 'djs:warehouse:stockFlow:export', '#', 1, NOW(), 'WMS-MAT-001');

-- 4.3 挂 mp applet 权限给 mp / staff / employee 角色（admin role_id=1 已含全权限，不动）
-- 沿 V202606061100 §2 范式：模糊匹配 role_key/role_name
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 9106 AS menu_id UNION ALL SELECT 9107 UNION ALL SELECT 9108) m
WHERE r.role_id <> 1
  AND r.del_flag = '0'
  AND (r.role_key LIKE '%mp%'
       OR r.role_key LIKE '%applet%'
       OR r.role_key LIKE '%staff%'
       OR r.role_key LIKE '%employee%'
       OR r.role_name LIKE '%员工%'
       OR r.role_name LIKE '%小程序%');
