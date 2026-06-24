-- FIX-WMS-FLOWDICT-002：补 djs_stock_out_dest 的「盘点计损」去向值
-- V202607240800 里 check_loss 的 dict_code=1006310 与 djs_abnormal 既有行撞键，INSERT IGNORE 静默跳过。改用未占号段 1006410。
INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1006410, '1001', 10, '盘点计损', 'check_loss', 'djs_stock_out_dest', '', 'danger', 'N', NULL, NOW(), 'FIX-WMS-FLOWDICT-002 盘点计损/异常出库去向（补 001 撞键漏插）');
