-- WSA-001 字典【猪肉产品退回】djs_pork_return_product（仓库×门店全流程对齐 阶段0 F0-1）
-- 用途：门店退回猪肉候选 / 盘点猪肉类 / 猪肉打包原料过滤 三处共用取数。
-- 约定（Kevin 2026-06-17 拍板）：label=产品名（admin 展示），value=产品业务编码 product_id（VARCHAR）。
--   后端查询时按 value 经 product_id resolve 成雪花主键（见 ProductInfoMapper.selectIdsByProductCodes）。
-- seed 现有 belong_type IN('pork','white_bar') 的标准产品作初值（排除测试/演示数据 123456 / D0001 / DEMO-*）；
--   客户后续可在 admin「字典管理」自行增删。
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (102607030, '1001', '猪肉产品退回', 'djs_pork_return_product', 1, NOW(),
        '门店退回/盘点/打包取数：label=产品名 value=product_id 业务码（WSA-001，客户可在 admin 增删）');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1026070301, '1001', 0, '白条·整只',   'PROD-WHITE-BAR-01', 'djs_pork_return_product', '', 'primary', 'N', 1, NOW()),
    (1026070302, '1001', 1, '白条·猪头',   'PROD-WHITE-BAR-02', 'djs_pork_return_product', '', 'primary', 'N', 1, NOW()),
    (1026070303, '1001', 2, '白条·猪蹄',   'PROD-WHITE-BAR-03', 'djs_pork_return_product', '', 'primary', 'N', 1, NOW()),
    (1026070304, '1001', 3, '白条·半只',   'PROD-WHITE-BAR-04', 'djs_pork_return_product', '', 'primary', 'N', 1, NOW()),
    (1026070305, '1001', 4, '猪肉·精瘦肉', 'PROD-PIG-LEAN-01',  'djs_pork_return_product', '', 'success', 'N', 1, NOW()),
    (1026070306, '1001', 5, '猪肉·部位肉', 'PROD-PIG-PART-01',  'djs_pork_return_product', '', 'success', 'N', 1, NOW()),
    (1026070307, '1001', 6, '猪肉·骨类',   'PROD-PIG-BONE-01',  'djs_pork_return_product', '', 'success', 'N', 1, NOW()),
    (1026070308, '1001', 7, '猪肉·猪皮',   'PROD-PIG-SKIN-01',  'djs_pork_return_product', '', 'success', 'N', 1, NOW()),
    (1026070309, '1001', 8, '猪肉·碎料',   'PROD-PIG-SCRAP-01', 'djs_pork_return_product', '', 'success', 'N', 1, NOW());
