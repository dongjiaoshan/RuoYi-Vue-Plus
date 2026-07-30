-- ============================================================================
-- DICT-ALIGN-004  地块类型 djs_plot_type 对齐 + 作物科 djs_crop_family 扩到 18 科
--
-- 1) 地块类型
--    a. nursery 的 label 甲方定为「育苗」（两库现在分别是「保育」/「育苗地」）。
--       ⚠️ dict_value 必须保持 nursery —— 后端 8 处硬编码按 'nursery' 过滤育苗地块
--       （FarmRecordsMapper / PlantOverviewMapper / AppletPlotPickerController /
--         PlantActivityServiceImpl），改 value 会直接打断 mp 移栽选地块。这里只改 label。
--    b. 新增「棚边 shed_edge」：甲方土地信息里的地块类型，字典缺项则生成器映射不出 value。
--       取号 1002014，续 1002010-1002013。
--
-- 2) 作物科 djs_crop_family
--    甲方作物信息实际用到 18 个科，字典只有 8 个。补 10 个（甲方 13 科清单里的「阿福花科」
--    数据 0 引用，不灌；6 个数据在用但甲方清单没列的科按实际数据补齐）。
--    取号 1002568-1002577，sort 8-17，续现有 0-7。
--    作物的「属」（如 芸薹属）是自由文本，走 t_plant_crop_info.crop_genus 列，不进字典。
--
-- 幂等：UPDATE 按 (dict_type, dict_value) 定位可重复执行；INSERT IGNORE 主键冲突即跳过。
--
-- 跑完刷 Redis 字典缓存：bash script/sql/djs/_post-init.sh
-- ============================================================================
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. 地块类型 djs_plot_type
-- ----------------------------------------------------------------------------
UPDATE sys_dict_data
   SET dict_label = '育苗'
 WHERE dict_type = 'djs_plot_type'
   AND dict_value = 'nursery';

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002014, '1001', 4, '棚边', 'shed_edge', 'djs_plot_type', '', 'info', 'N', NULL, NOW());

-- ----------------------------------------------------------------------------
-- 2. 作物科 djs_crop_family（补 10 科 → 共 18 科）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002568, '1001',  8, '伞形科',   'apiaceae',       'djs_crop_family', '', 'primary', 'N', NULL, NOW()),
  (1002569, '1001',  9, '藜科',     'chenopodiaceae', 'djs_crop_family', '', 'primary', 'N', NULL, NOW()),
  (1002570, '1001', 10, '唇形科',   'lamiaceae',      'djs_crop_family', '', 'success', 'N', NULL, NOW()),
  (1002571, '1001', 11, '番杏科',   'aizoaceae',      'djs_crop_family', '', 'success', 'N', NULL, NOW()),
  (1002572, '1001', 12, '苋科',     'amaranthaceae',  'djs_crop_family', '', 'warning', 'N', NULL, NOW()),
  (1002573, '1001', 13, '落葵科',   'basellaceae',    'djs_crop_family', '', 'warning', 'N', NULL, NOW()),
  (1002574, '1001', 14, '蔷薇科',   'rosaceae',       'djs_crop_family', '', 'info',    'N', NULL, NOW()),
  (1002575, '1001', 15, '羊肚菌科', 'morchellaceae',  'djs_crop_family', '', 'info',    'N', NULL, NOW()),
  (1002576, '1001', 16, '锦葵科',   'malvaceae',      'djs_crop_family', '', 'info',    'N', NULL, NOW()),
  (1002577, '1001', 17, '马齿苋科', 'portulacaceae',  'djs_crop_family', '', 'info',    'N', NULL, NOW());
