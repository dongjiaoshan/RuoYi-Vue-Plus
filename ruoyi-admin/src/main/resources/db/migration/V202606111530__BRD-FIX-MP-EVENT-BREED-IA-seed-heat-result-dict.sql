-- BRD-FIX-MP-EVENT-BREED-IA-001：补 seed djs_heat_result 查情结果字典。
-- 根因：V202606111300（D10X 5 字典 seed）里 djs_heat_result 用 dict_id=100630，但 100630 已被
--   djs_stock_out_dest 占用 → INSERT IGNORE 静默忽略，type/data 行均未落地（DB 实测 0 行）。
-- 修复：改用未占用 dict_id=100635 + dict_code 1006350~1006352 重新 seed。
-- 码值来源：mp heat/index.vue 原 inline options（POS 阳性 / NEG 阴性 / PEND 待复查）权威，
--   阳性触发状态机 PZ→PH（与 HeatBo.isPregnantConfirmed 语义一致）。
-- ADR-0004 §2.1 命名 djs_<业务域>_<维度>；§2.3 seed-first。
-- 跑完必须刷 Redis 字典缓存：bash code/main/RuoYi-Vue-Plus/script/sql/djs/_post-init.sh

INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100635, '1001', '查情结果', 'djs_heat_result', NULL, NOW(), '养殖：查情录入结果，mp heat 页 inline options 权威（阳性触发 PZ→PH）');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006350, '1001', 0, '阳性',   'POS',  'djs_heat_result', '', 'success', 'Y', NULL, NOW()),
  (1006351, '1001', 1, '阴性',   'NEG',  'djs_heat_result', '', 'info',    'N', NULL, NOW()),
  (1006352, '1001', 2, '待复查', 'PEND', 'djs_heat_result', '', 'warning', 'N', NULL, NOW());
