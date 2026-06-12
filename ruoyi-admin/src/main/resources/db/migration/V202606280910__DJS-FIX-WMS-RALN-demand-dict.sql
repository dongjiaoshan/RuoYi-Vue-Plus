-- DJS-FIX-WMS-RALN-C 需求管理单页 7 产品 tab：djs_demand_product_type 4 业态 → 7 业态
-- 原 4 值（white_bar/vegetable/gift_box/other）补 3 值：pig(猪) / dry(干货) / egg(鸡蛋)
-- 与原型「新增需求」7 tab 逐字对齐：白条/猪/果蔬/干货/鸡蛋/礼盒/其他
-- 幂等：先 DELETE 整个 dict_type 再全量重插（不依赖运行序，重复跑结果一致）
SET NAMES utf8mb4;

DELETE FROM sys_dict_data WHERE dict_type = 'djs_demand_product_type';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_demand_product_type';

INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, remark)
VALUES (920402, '1001', '需求业态', 'djs_demand_product_type', 1, 'DJS-FIX-WMS-RALN-C 7 业态');

INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, remark)
VALUES
    (92040201, '1001', 1, '白条', 'white_bar', 'djs_demand_product_type', '', 'danger',  'Y', 1, '需指定猪只'),
    (92040202, '1001', 2, '猪',   'pig',       'djs_demand_product_type', '', 'danger',  'N', 1, '需指定猪只'),
    (92040203, '1001', 3, '果蔬', 'vegetable', 'djs_demand_product_type', '', 'success', 'N', 1, ''),
    (92040204, '1001', 4, '干货', 'dry',       'djs_demand_product_type', '', 'info',    'N', 1, ''),
    (92040205, '1001', 5, '鸡蛋', 'egg',       'djs_demand_product_type', '', 'warning', 'N', 1, ''),
    (92040206, '1001', 6, '礼盒', 'gift_box',  'djs_demand_product_type', '', 'warning', 'N', 1, '内部使用'),
    (92040207, '1001', 7, '其他', 'other',     'djs_demand_product_type', '', 'info',    'N', 1, '');
