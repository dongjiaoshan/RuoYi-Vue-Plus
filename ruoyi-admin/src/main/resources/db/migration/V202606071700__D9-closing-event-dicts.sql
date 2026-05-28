-- D9 closing+1 hotfix: seed 6 个事件录入页缺失字典
-- 触发：mp 1.1 出栏录入 / 1.3 死亡录入 / 1.4 淘汰录入 / 配种录入 点字典下拉全空白
-- 根因：D6 hotfix 写 mp schema 时用了 6 个 dict_type（djs_out_house_dest / djs_death_reason / djs_death_dest / djs_eliminate_reason / djs_eliminate_dest / djs_breeding_type），但 D2 SYS-INIT-002 没 seed；其中 djs_eliminate_reason 与 D2 已有 djs_elimination_reason 同义异名
-- 处置：
--   1. 5 个全新 seed：djs_out_house_dest / djs_death_reason / djs_death_dest / djs_eliminate_dest / djs_breeding_type
--   2. djs_eliminate_reason — 复用 D2 djs_elimination_reason 的 4 条值（年龄 / 疾病 / 性能 / 其他），新 dict_type 重复 seed 一份；最终保留哪个 key 由 D9 _open-issues MP-IA-S0-09 决策
-- ADR：ADR-0004 §2.1 命名规范 djs_<业务域>_<维度>；新 6 条命名以 mp schema 既成事实为准（11 个事件页 + java javadoc 已 1.5 周固化）
-- 跑完必须刷 Redis 字典缓存（详 .claude/CLAUDE.md §5）：bash code/main/RuoYi-Vue-Plus/script/sql/djs/_post-init.sh

-- ------------------------------------------------------------
-- 1. djs_out_house_dest 出栏去向
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100620, '1001', '出栏去向', 'djs_out_house_dest', NULL, NOW(), '养殖：肥猪出栏目的地（送宰/外销/其他），mp BRD-EVENT-004 SLAUGHTER 用');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006200, '1001', 0, '送宰',   'slaughter', 'djs_out_house_dest', '', 'primary', 'Y', NULL, NOW()),
  (1006201, '1001', 1, '外销',   'sale_out',  'djs_out_house_dest', '', 'success', 'N', NULL, NOW()),
  (1006202, '1001', 2, '其他',   'other',     'djs_out_house_dest', '', '',        'N', NULL, NOW());

-- ------------------------------------------------------------
-- 2. djs_death_reason 死亡原因
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100621, '1001', '死亡原因', 'djs_death_reason', NULL, NOW(), '养殖：猪只死亡分类，mp BRD-EVENT-004 DIE 用；客户后续可在 admin 字典页扩展粒度');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006210, '1001', 0, '疾病',     'disease',  'djs_death_reason', '', 'danger',  'Y', NULL, NOW()),
  (1006211, '1001', 1, '意外',     'accident', 'djs_death_reason', '', 'warning', 'N', NULL, NOW()),
  (1006212, '1001', 2, '非洲猪瘟', 'asf',      'djs_death_reason', '', 'danger',  'N', NULL, NOW()),
  (1006213, '1001', 3, '其他',     'other',    'djs_death_reason', '', '',        'N', NULL, NOW());

-- ------------------------------------------------------------
-- 3. djs_death_dest 死亡去向
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100622, '1001', '死亡去向', 'djs_death_dest', NULL, NOW(), '养殖：猪只死亡处置方向，mp BRD-EVENT-004 DIE 用');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006220, '1001', 0, '无害化', 'harmless', 'djs_death_dest', '', 'info',    'Y', NULL, NOW()),
  (1006221, '1001', 1, '食用',   'edible',   'djs_death_dest', '', 'warning', 'N', NULL, NOW()),
  (1006222, '1001', 2, '其他',   'other',    'djs_death_dest', '', '',        'N', NULL, NOW());

-- ------------------------------------------------------------
-- 4. djs_eliminate_reason 淘汰原因（与 D2 已 seed 的 djs_elimination_reason 同义异名）
--    复用 D2 4 条 value（age / disease / performance / other）以保持下游业务码兼容
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100623, '1001', '淘汰原因', 'djs_eliminate_reason', NULL, NOW(), '养殖：淘汰事件原因；与 D2 djs_elimination_reason 同义异名，最终命名由 MP-IA-S0-09 决策');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006230, '1001', 0, '年龄', 'age',         'djs_eliminate_reason', '', 'info',    'N', NULL, NOW()),
  (1006231, '1001', 1, '疾病', 'disease',     'djs_eliminate_reason', '', 'danger',  'Y', NULL, NOW()),
  (1006232, '1001', 2, '性能', 'performance', 'djs_eliminate_reason', '', 'warning', 'N', NULL, NOW()),
  (1006233, '1001', 3, '其他', 'other',       'djs_eliminate_reason', '', '',        'N', NULL, NOW());

-- ------------------------------------------------------------
-- 5. djs_eliminate_dest 淘汰去向
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100624, '1001', '淘汰去向', 'djs_eliminate_dest', NULL, NOW(), '养殖：淘汰事件去向，mp BRD-EVENT-004 ELIMINATE 用');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006240, '1001', 0, '屠宰',   'slaughter', 'djs_eliminate_dest', '', 'primary', 'Y', NULL, NOW()),
  (1006241, '1001', 1, '外卖',   'sale_out',  'djs_eliminate_dest', '', 'success', 'N', NULL, NOW()),
  (1006242, '1001', 2, '无害化', 'harmless',  'djs_eliminate_dest', '', 'info',    'N', NULL, NOW()),
  (1006243, '1001', 3, '其他',   'other',     'djs_eliminate_dest', '', '',        'N', NULL, NOW());

-- ------------------------------------------------------------
-- 6. djs_breeding_type 配种方式
--    mp schema hint："1 本场 / 2 精液 / AI 人工 / LQ 冷冻 / RJ 鲜精"
--    java doc 注释：「1=本场公猪 2=精液产品；本工程使用 varchar(16) 允许扩展」
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100625, '1001', '配种方式', 'djs_breeding_type', NULL, NOW(), '养殖：BRD-EVENT-002 BREED 配种事件入参 breedingType；varchar(16) 允许扩展');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006250, '1001', 0, '本场公猪', '1',  'djs_breeding_type', '', 'primary', 'Y', NULL, NOW()),
  (1006251, '1001', 1, '精液产品', '2',  'djs_breeding_type', '', 'success', 'N', NULL, NOW()),
  (1006252, '1001', 2, '人工授精', 'AI', 'djs_breeding_type', '', 'warning', 'N', NULL, NOW()),
  (1006253, '1001', 3, '冷冻精液', 'LQ', 'djs_breeding_type', '', 'info',    'N', NULL, NOW()),
  (1006254, '1001', 4, '鲜精',     'RJ', 'djs_breeding_type', '', '',        'N', NULL, NOW());
