-- ============================================================================
-- V6 row134 / row135  追溯页「生长记录」「农事记录」入口的显示门槛（单值配置字典）
--
-- djs_trace_grow_show_min：猪的生长记录条数 < 该值 → C 端猪肉追溯页不显示生长记录入口。
-- djs_trace_farm_show_min：地块农事记录条数 < 该值 → C 端果蔬追溯页不显示农事记录入口。
-- 两者 dict_value 都存纯数字、默认 3，客户在 admin「字典管理」里直接改。
--
-- 单值配置字典写法照 djs_piglet_default_weight（每类只放一行 dict_data，
-- 后端 readShowMin 取该类第一行的 dict_value）。
--
-- 取号：通用扩展字典段 1034xxx / 1035xxx（两库实查为空，未与养殖 1001xxx / 1033xxx 段重叠）。
-- 幂等：INSERT IGNORE，重跑不改客户已经调过的值。
--
-- 跑完刷 Redis 字典缓存：bash script/sql/djs/_post-init.sh
-- ============================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (103400, '1001', '猪肉生长记录显示配置', 'djs_trace_grow_show_min', NULL, NOW(),
   '追溯：猪肉追溯页「生长记录」入口的显示门槛，值为最少记录条数，达到才显示，默认 3'),
  (103500, '1001', '农事记录显示配置', 'djs_trace_farm_show_min', NULL, NOW(),
   '追溯：果蔬追溯页「作物农事记录」入口的显示门槛，值为最少记录条数，达到才显示，默认 3');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
  (1034000, '1001', 0, '最少记录条数', '3', 'djs_trace_grow_show_min', '', 'primary', 'Y', NULL, NOW(),
   '猪只生长记录满几条才在追溯页显示生长记录入口'),
  (1035000, '1001', 0, '最少记录条数', '3', 'djs_trace_farm_show_min', '', 'primary', 'Y', NULL, NOW(),
   '地块农事记录满几条才在追溯页显示农事记录入口');
