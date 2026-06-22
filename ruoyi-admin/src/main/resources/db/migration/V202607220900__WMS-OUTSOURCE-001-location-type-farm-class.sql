-- WMS-OUTSOURCE-001：djs_location_type 补 种植库 crop_loc / 养殖库 farm_loc
-- 物资领用按库位类型组织（mp issueLocationsByType / issueItemsByType 端点要用）。
-- dict_code 取 1027000/1027001（1020009 起后续已被其他字典占，改用未占的 1027xxx 段）。
-- 幂等：先 DELETE 再 INSERT（本地新增 / staging 重对齐都安全；staging 已有这俩 value 时不撞唯一性）。
DELETE FROM sys_dict_data WHERE dict_type = 'djs_location_type' AND dict_value IN ('crop_loc', 'farm_loc');

INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time)
VALUES
    (1027000, '1001', 8, '种植库', 'crop_loc', 'djs_location_type', '', 'success', 'N', 103, 1, NOW()),
    (1027001, '1001', 9, '养殖库', 'farm_loc', 'djs_location_type', '', 'primary', 'N', 103, 1, NOW());
