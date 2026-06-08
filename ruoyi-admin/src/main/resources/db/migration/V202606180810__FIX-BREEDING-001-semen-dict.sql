-- FIX-BREEDING-001 #21：精液改纯字典下拉（Kevin 2026-06-08 裁决 a：不扣库存、不关联仓库 product）。
-- 1) 新增 t_farm_pig_breeding.semen_code 列（精液字典 code），仅新增列、不动既有列。
-- 2) seed djs_semen 精液字典（dict_type + dict_data），供配种类型=精液时下拉选用。
-- ADR-0004 §2.1 命名 djs_<业务域>_<维度>（djs_semen 精液产品维度）；§2.3 seed-first 集中维护。
-- 跑完必须刷 Redis 字典缓存：bash code/main/RuoYi-Vue-Plus/script/sql/djs/_post-init.sh

-- 1. 配种记录表加精液字典 code 列（可空：仅 breedingType=精液 时填）
ALTER TABLE t_farm_pig_breeding
    ADD COLUMN semen_code VARCHAR(64) NULL COMMENT '配种精液字典code（djs_semen；breedingType=精液时填）';

-- 2. seed djs_semen 精液字典（dict_id=100640 未占用；dict_code 1006400 起）
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100640, '1001', '配种精液', 'djs_semen', NULL, NOW(), '养殖：配种类型=精液时的精液产品下拉（FIX-BREEDING-001，纯字典、不扣库存）');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006400, '1001', 0, '杜洛克精液',     'DLK', 'djs_semen', '', 'primary', 'Y', NULL, NOW()),
  (1006401, '1001', 1, '长白精液',       'CB',  'djs_semen', '', 'success', 'N', NULL, NOW()),
  (1006402, '1001', 2, '大白精液',       'DB',  'djs_semen', '', 'info',    'N', NULL, NOW()),
  (1006403, '1001', 3, '皮特兰精液',     'PTL', 'djs_semen', '', 'warning', 'N', NULL, NOW());
