-- FIX-BELONG-TYPE-OTHER-SEED-001：djs_belong_type 增「其他」值
--
-- 门店需求创建页 7 业态 tab（白条/猪肉/果蔬/干货/鸡蛋/礼盒/其他）按 djs_belong_type 分组展示，
-- 字典缺 'other' 导致「其他」业态产品无归属类型可选。
-- dict_sort 接续 8（seed）之后 = 9；dict_code 续 1020048 之后 = 1020049。
-- 用 DELETE 同 (dict_type,dict_value) 再 INSERT 防重（可重复执行）。

SET NAMES utf8mb4;

DELETE FROM sys_dict_data WHERE dict_type = 'djs_belong_type' AND dict_value = 'other';

INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1020049, '1001', 9, '其他', 'other', 'djs_belong_type', '', 'info', 'N', 1, NOW(), 'FIX-BELONG-TYPE-OTHER-SEED-001');

-- 应用后需跑 bash script/sql/djs/_post-init.sh flush redis 字典缓存
