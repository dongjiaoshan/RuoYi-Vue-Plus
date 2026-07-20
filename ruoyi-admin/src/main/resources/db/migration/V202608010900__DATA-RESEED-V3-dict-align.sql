-- DATA-RESEED-V3：字典对齐（配合甲方 kdocs v3 初始数据）
--   1) djs_location_type 补「仓库库 warehouse」（v3 库位 10 个泛化为通用仓储类型）
--   2) djs_product_workshop 补「礼盒打包间 7」（v3 礼盒成品生产车间）
--   3) djs_death_reason 用甲方 10 个细症状替换旧 4 粗类（疾病/意外/非洲猪瘟/其他）
-- deathReason 后端为自由 varchar（无值分支逻辑），替换安全。
-- 幂等：先 DELETE 再 INSERT（本地新增 / staging 重对齐都安全）。dict_code 取未占的 1028xxx 段。

-- 1) 库位类型：仓库库
DELETE FROM sys_dict_data WHERE dict_type = 'djs_location_type' AND dict_value = 'warehouse';
INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time)
VALUES
    (1028000, '1001', 10, '仓库库', 'warehouse', 'djs_location_type', '', 'info', 'N', 103, 1, NOW());

-- 2) 生产车间：礼盒打包间（值 7，续 1-6）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_product_workshop' AND dict_value = '7';
INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time)
VALUES
    (1028010, '1001', 7, '礼盒打包间', '7', 'djs_product_workshop', '', 'primary', 'N', 103, 1, NOW());

-- 3) 死亡原因：细症状替换（删旧 4 粗类，灌甲方 10 细症状）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_death_reason';
INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time)
VALUES
    (1028020, '1001', 1,  '胀气',           '1',  'djs_death_reason', '', 'default', 'N', 103, 1, NOW()),
    (1028021, '1001', 2,  '回肠炎',         '2',  'djs_death_reason', '', 'default', 'N', 103, 1, NOW()),
    (1028022, '1001', 3,  '急性胸膜肺炎',   '3',  'djs_death_reason', '', 'default', 'N', 103, 1, NOW()),
    (1028023, '1001', 4,  '高烧',           '4',  'djs_death_reason', '', 'default', 'N', 103, 1, NOW()),
    (1028024, '1001', 5,  '腹泻',           '5',  'djs_death_reason', '', 'default', 'N', 103, 1, NOW()),
    (1028025, '1001', 6,  '呼吸道问题',     '6',  'djs_death_reason', '', 'default', 'N', 103, 1, NOW()),
    (1028026, '1001', 7,  '瘦弱(长期不食)', '7',  'djs_death_reason', '', 'default', 'N', 103, 1, NOW()),
    (1028027, '1001', 8,  '皮肤苍白',       '8',  'djs_death_reason', '', 'default', 'N', 103, 1, NOW()),
    (1028028, '1001', 9,  '神经症状',       '9',  'djs_death_reason', '', 'default', 'N', 103, 1, NOW()),
    (1028029, '1001', 10, '突然死亡',       '10', 'djs_death_reason', '', 'default', 'N', 103, 1, NOW());
