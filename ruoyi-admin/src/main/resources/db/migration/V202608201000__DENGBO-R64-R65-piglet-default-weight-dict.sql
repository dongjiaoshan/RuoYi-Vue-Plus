-- DENGBO R64/R65：新增字典「默认仔猪重量」djs_piglet_default_weight
-- 用途：mp 养殖录入页取默认值预填——耳标标记页「出生重」预填 2，断奶录入页「断奶重」预填 5。
-- 值为可配置默认（客户在 admin 字典管理改 dict_value 即改预填），dict_value 存数字（kg）。
-- ADR-0004 §2.1 命名 djs_<业务域>_<维度>；§2.3 seed-first 集中维护。
-- 跑完必须刷 Redis 字典缓存：bash code/main/RuoYi-Vue-Plus/script/sql/djs/_post-init.sh

INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (103300, '1001', '默认仔猪重量', 'djs_piglet_default_weight', NULL, NOW(), '养殖：仔猪录入默认重量（kg），出生重预填耳标页、断奶重预填断奶页，客户可改');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1033000, '1001', 0, '仔猪出生重', '2', 'djs_piglet_default_weight', '', 'primary', 'Y', NULL, NOW()),
  (1033001, '1001', 1, '仔猪断奶重', '5', 'djs_piglet_default_weight', '', 'success', 'N', NULL, NOW());
