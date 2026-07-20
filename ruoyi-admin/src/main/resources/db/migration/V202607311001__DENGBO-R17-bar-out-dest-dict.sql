-- ============================================================
-- DENGBO-R17  白条领用「仓库出库」出库去向字典 djs_bar_out_dest
-- ============================================================
-- row17：白条领用页新增出库位置「仓库出库」，选中后需选「出库去向」。
-- 出库方式=后台出库（记 product_production.remark），出库去向走本字典。
-- dict_id / dict_code 取空号段：dict_id=1007005，dict_code=1007050-1007052。
-- ⚠️ 占位值：其他仓库 / 暂存冷库 / 损耗处理 —— 真实去向枚举待邓博确认后按需增删改。
-- 跑后须 bash script/sql/djs/_post-init.sh flush redis 字典缓存。
-- ============================================================

INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (1007005, '1001', '白条出库去向', 'djs_bar_out_dest', 1, NOW(), 'DENGBO-R17 白条领用仓库出库去向（占位值待邓博确认）');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1007050, '1001', 1, '其他仓库', 'other_warehouse', 'djs_bar_out_dest', '', 'primary', 'N', 1, NOW(), '占位：转其他仓库（待邓博确认真实枚举）'),
    (1007051, '1001', 2, '暂存冷库', 'cold_storage',    'djs_bar_out_dest', '', 'info',    'N', 1, NOW(), '占位：暂存冷库（待邓博确认真实枚举）'),
    (1007052, '1001', 3, '损耗处理', 'loss_handle',     'djs_bar_out_dest', '', 'warning', 'N', 1, NOW(), '占位：损耗处理（待邓博确认真实枚举）');
