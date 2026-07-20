-- DJS-FIX-WMS-RALN-B：入库记录 / 出库记录页「入库方式 / 出库方式」字典补齐（对齐原型）。
--
-- 原型「库存管理/入库记录」入库方式列 = 接收入库 / 退货入库；
-- 原型「库存管理/出库记录」出库方式列 = 领用出库 / 后台出库。
-- 现 djs_flow_type 已有 领用出库(pick_out)，缺 接收入库 / 退货入库 / 后台出库 三值，
-- 补入后入/出库记录页方式列 dict-tag 可正常翻译，方式筛选下拉可选。
--
-- receive_in    = 接收入库（IN，绿 success，外部接收/到货入库）
-- return_goods_in = 退货入库（IN，蓝 primary，退货回库；区别于物资 return_in 退回入库）
-- backstage_out = 后台出库（OUT，灰 info，后台手工出库）
INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (102450017, '1001', 17, '接收入库', 'receive_in',      'djs_flow_type', '', 'success', 'N', 1, NOW(), 'DJS-FIX-WMS-RALN-B 入库记录入库方式'),
    (102450018, '1001', 18, '退货入库', 'return_goods_in', 'djs_flow_type', '', 'primary', 'N', 1, NOW(), 'DJS-FIX-WMS-RALN-B 入库记录入库方式'),
    (102450019, '1001', 19, '后台出库', 'backstage_out',   'djs_flow_type', '', 'info',    'N', 1, NOW(), 'DJS-FIX-WMS-RALN-B 出库记录出库方式');
