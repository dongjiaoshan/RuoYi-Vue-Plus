-- D10X bug fix: seed 5 个被 mp/admin 事件页引用但 DB 从未 seed 的字典
-- 触发：深度审计发现 djs_heat_result / djs_abnormal / djs_null_return / djs_vaccine_type / djs_death_kind
--       在 mp 事件页（查情 / 返空 / 死亡 / 用药治疗）以 hint / 注释 / 字段语义引用，
--       但 sys_dict_data 0 行（doc/11 曾标 🟢 D2/D6，实际 seed 从未落地）。
-- 影响：当前 mp 这几页用 inline 硬编码 options 兜住没白屏，但 admin 事件列表 dict-tag
--       渲染 raw value、且 IA 决策后改 dict 驱动时会整列空白。先把字典码值落地对齐。
-- 码值来源：
--   1. djs_heat_result — mp heat/index.vue inline options（POS/NEG/PEND）权威
--   2. djs_abnormal    — t_farm_pig_abnormal.abnormal_type DB 存值 R/A/N（entity 注释 + 原型「返情/空怀/流产」权威）
--   3. djs_null_return — 客户未给具体值，按业内常见返空原因 seed 合理默认（待客户核）
--   4. djs_vaccine_type — 客户 xlsx「(空，待填)」，按猪场常规疫苗 seed 合理默认（待客户核）
--   5. djs_death_kind   — mp die/index.vue inline options（normal/abnormal）+ deathKind 字段语义权威
-- 命名 vs doc/11 差异（closing 需对账统一，本文以 mp 既成事实 + 审计请求名为准）：
--   djs_abnormal   取代 doc/11 djs_abnormal_type（异常类型，码值 R/A/N 不变）
--   djs_death_kind 取代 doc/11 djs_death_type（死亡类型，码值 normal/abnormal 不变）
-- ADR：ADR-0004 §2.1 命名 djs_<业务域>_<维度>；§2.3 seed-first 集中维护
-- 跑完必须刷 Redis 字典缓存（详 .claude/CLAUDE.md §5）：bash code/main/RuoYi-Vue-Plus/script/sql/djs/_post-init.sh

-- ------------------------------------------------------------
-- 1. djs_heat_result 查情结果（mp BRD-EVENT-002 OESTRUS 查情录入用）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100630, '1001', '查情结果', 'djs_heat_result', NULL, NOW(), '养殖：查情录入结果，mp heat 页 inline options 权威（阳性触发 PZ→PH）');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006300, '1001', 0, '阳性',   'POS',  'djs_heat_result', '', 'success', 'Y', NULL, NOW()),
  (1006301, '1001', 1, '阴性',   'NEG',  'djs_heat_result', '', 'info',    'N', NULL, NOW()),
  (1006302, '1001', 2, '待复查', 'PEND', 'djs_heat_result', '', 'warning', 'N', NULL, NOW());

-- ------------------------------------------------------------
-- 2. djs_abnormal 异常类型（mp BRD-EVENT-002 NULL_RETURN 返空录入；DB 存 R/A/N）
--    取代 doc/11 djs_abnormal_type；码值对齐 t_farm_pig_abnormal.abnormal_type
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100631, '1001', '异常类型', 'djs_abnormal', NULL, NOW(), '养殖：返空流异常类型，DB 存值 R=返情/A=流产/N=空怀，对应状态机 FQ/LC/KH');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006310, '1001', 0, '返情', 'R', 'djs_abnormal', '', 'warning', 'Y', NULL, NOW()),
  (1006311, '1001', 1, '流产', 'A', 'djs_abnormal', '', 'danger',  'N', NULL, NOW()),
  (1006312, '1001', 2, '空怀', 'N', 'djs_abnormal', '', 'info',    'N', NULL, NOW());

-- ------------------------------------------------------------
-- 3. djs_null_return 返空原因（mp NULL_RETURN abnormalReason 选填；客户未给值，业内默认）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100632, '1001', '返空原因', 'djs_null_return', NULL, NOW(), '养殖：返空流原因细分，客户未给具体值，按业内常见 seed 默认，待客户核');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006320, '1001', 0, '配种失败', 'mating_fail',   'djs_null_return', '', 'warning', 'Y', NULL, NOW()),
  (1006321, '1001', 1, '早期流产', 'early_abort',   'djs_null_return', '', 'danger',  'N', NULL, NOW()),
  (1006322, '1001', 2, '胚胎死亡', 'embryo_death',  'djs_null_return', '', 'danger',  'N', NULL, NOW()),
  (1006323, '1001', 3, '疾病影响', 'disease',       'djs_null_return', '', 'danger',  'N', NULL, NOW()),
  (1006324, '1001', 4, '营养不良', 'malnutrition',  'djs_null_return', '', 'info',    'N', NULL, NOW()),
  (1006325, '1001', 5, '其他',     'other',         'djs_null_return', '', '',        'N', NULL, NOW());

-- ------------------------------------------------------------
-- 4. djs_vaccine_type 疫苗类型（mp 用药治疗页「疫苗类型」选择；客户 xlsx 空，业内默认）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100633, '1001', '疫苗类型', 'djs_vaccine_type', NULL, NOW(), '养殖：疫苗分类，客户 xlsx 未填，按生猪场常规免疫程序 seed 默认，待客户核');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006330, '1001', 0, '猪瘟疫苗',     'classical_swine_fever', 'djs_vaccine_type', '', 'primary', 'Y', NULL, NOW()),
  (1006331, '1001', 1, '口蹄疫疫苗',   'foot_mouth',            'djs_vaccine_type', '', 'primary', 'N', NULL, NOW()),
  (1006332, '1001', 2, '蓝耳病疫苗',   'prrs',                  'djs_vaccine_type', '', 'success', 'N', NULL, NOW()),
  (1006333, '1001', 3, '伪狂犬疫苗',   'pseudorabies',          'djs_vaccine_type', '', 'success', 'N', NULL, NOW()),
  (1006334, '1001', 4, '圆环病毒疫苗', 'circovirus',            'djs_vaccine_type', '', 'warning', 'N', NULL, NOW()),
  (1006335, '1001', 5, '支原体疫苗',   'mycoplasma',            'djs_vaccine_type', '', 'warning', 'N', NULL, NOW()),
  (1006336, '1001', 6, '细小病毒疫苗', 'parvovirus',            'djs_vaccine_type', '', 'info',    'N', NULL, NOW()),
  (1006337, '1001', 7, '其他',         'other',                 'djs_vaccine_type', '', '',        'N', NULL, NOW());

-- ------------------------------------------------------------
-- 5. djs_death_kind 死亡类型（mp BRD-EVENT-004 DIE 死亡录入「死亡分类」；DB 存 normal/abnormal）
--    取代 doc/11 djs_death_type；码值对齐 t_farm_pig_death.death_kind + mp die 页 inline options
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100634, '1001', '死亡类型', 'djs_death_kind', NULL, NOW(), '养殖：猪只死亡分类，DB 存值 normal=正常/abnormal=异常，mp die 页 inline options 权威');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006340, '1001', 0, '正常死亡', 'normal',   'djs_death_kind', '', 'info',   'Y', NULL, NOW()),
  (1006341, '1001', 1, '异常死亡', 'abnormal', 'djs_death_kind', '', 'danger', 'N', NULL, NOW());
