-- ============================================================================
-- DICT-ALIGN-005  灾害类型补「草害」 + 用药类型 label 两库统一
--
-- 1) djs_disaster_type：甲方「字典项确认清单」给的是 虫害/草害/风灾/洪灾/旱灾/病害 6 项，
--    系统只有 5 项（缺草害）。补 weed；甲方的「洪灾/旱灾」与系统「洪涝/干旱」是措辞差异，
--    value 稳定（flood / drought），措辞不动。
--    取号：djs_disaster_type 现占 1026300-1026304，1026305 起两库皆空。
--
-- 2) djs_medicine_use_type：value='vaccine' 的 label 本地是「疫苗」、staging 是「免疫」，
--    甲方清单写的是「免疫」，统一成「免疫」。按 value 定位，value 不动（代码按 value 取值）。
--
-- 跑完刷 Redis 字典缓存：bash script/sql/djs/_post-init.sh
-- ============================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
  (1026305, '1001', 5, '草害', 'weed', 'djs_disaster_type', '', 'success', 'N', NULL, NOW(), '杂草危害');

UPDATE sys_dict_data
   SET dict_label = '免疫', update_time = NOW()
 WHERE dict_type = 'djs_medicine_use_type' AND tenant_id = '1001' AND dict_value = 'vaccine';
