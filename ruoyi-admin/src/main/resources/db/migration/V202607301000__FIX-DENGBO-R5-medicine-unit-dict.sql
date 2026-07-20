-- FIX-DENGBO-R5  用药领用·用药量单位字典（djs_medicine_unit）
-- 邓博 小程序 row5：用药量单位改下拉、从字典取，默认 ml，可选 ml/L/mg/g/kg/片。
-- 幂等 DELETE+INSERT；新部署 Flyway 自动跑，staging（flyway 关）已手工 apply + flush redis。
-- 配套：DictTypeConstants.MEDICINE_UNIT 登记进 mp 字典白名单。

DELETE FROM sys_dict_data WHERE dict_type = 'djs_medicine_unit';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_medicine_unit';

INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (1088000, '1001', '用药量单位', 'djs_medicine_unit', 1, NOW(), 'FIX-DENGBO-R5 用药领用单位下拉');

INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1088001, '1001', 0, 'ml', 'ml', 'djs_medicine_unit', '', 'primary', 'Y', 1, NOW(), '默认'),
    (1088002, '1001', 1, 'L',  'L',  'djs_medicine_unit', '', 'default', 'N', 1, NOW(), ''),
    (1088003, '1001', 2, 'mg', 'mg', 'djs_medicine_unit', '', 'default', 'N', 1, NOW(), ''),
    (1088004, '1001', 3, 'g',  'g',  'djs_medicine_unit', '', 'default', 'N', 1, NOW(), ''),
    (1088005, '1001', 4, 'kg', 'kg', 'djs_medicine_unit', '', 'default', 'N', 1, NOW(), ''),
    (1088006, '1001', 5, '片', '片', 'djs_medicine_unit', '', 'default', 'N', 1, NOW(), '');
