-- WMS-DEMAND-001 字典 seed
-- djs_demand_status 7 态已由 D7 WMS-MD-001 (V202605290910) seed，本文件不重复
-- 仅新建 djs_demand_product_type 4 业态（业务字段维度，区别于 djs_product_type 产品维度 1/2/3）
-- 列对齐 sys_dict_type / sys_dict_data 实际表结构（无 status 列）

INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, remark)
VALUES (920402, '1001', '需求业态', 'djs_demand_product_type', 1, 'WMS-DEMAND-001 4 业态');

INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, remark)
VALUES
    (92040201, '1001', 1, '白条', 'white_bar', 'djs_demand_product_type', '', 'danger',  'Y', 1, '需指定猪只'),
    (92040202, '1001', 2, '蔬菜', 'vegetable', 'djs_demand_product_type', '', 'success', 'N', 1, ''),
    (92040203, '1001', 3, '礼盒', 'gift_box',  'djs_demand_product_type', '', 'warning', 'N', 1, '内部使用'),
    (92040204, '1001', 4, '其他', 'other',     'djs_demand_product_type', '', 'info',    'N', 1, '');
