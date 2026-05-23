-- ============================================================
-- D05 Closing 修复补丁
-- 修复 D5 实施中发现的 5 个数据问题（_open-issues.md 待决项）
--
--   1. menu_id 7103 名称重复（"引种登记" → "引种新增"）
--   2. BRD-CORE-001 菜单 7200-7202 补赋 breed_admin / breed_worker
--   3. djs_pig_gender dict_value 对齐 DB（male/female → M/F）
--   4. 补种 djs_pig_type 字典（4 值）
--   5. 补种 djs_pig_end_reason 字典（3 值）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- Fix 1: menu_id 7103 名称去重
--   7100 = 引种登记（二级目录）
--   7103 = 引种新增（三级按钮，perms djs:breed:intro:add）
-- ------------------------------------------------------------
UPDATE sys_menu SET menu_name = '引种新增' WHERE menu_id = 7103;

-- ------------------------------------------------------------
-- Fix 2: BRD-CORE-001 猪只主表菜单补赋 breed_admin / breed_worker
--   V202605260900 源文件引用了不存在的 pig_keeper / butcher
--   运行库只赋了 boss + manager，breed_worker 看不到猪只主表菜单
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 7200 AS menu_id UNION SELECT 7201 UNION SELECT 7202) m
WHERE r.role_key IN ('breed_admin', 'breed_worker');

-- ------------------------------------------------------------
-- Fix 3: djs_pig_gender dict_value 对齐 DB
--   t_farm_pig_info.pig_sex CHAR(1) 存 'F'/'M'
--   原字典值 male/female 与 DB 不一致导致 dict-tag 显示为空
-- ------------------------------------------------------------
UPDATE sys_dict_data
SET dict_value = 'M'
WHERE dict_type = 'djs_pig_gender' AND dict_label = '公';

UPDATE sys_dict_data
SET dict_value = 'F'
WHERE dict_type = 'djs_pig_gender' AND dict_label = '母';

-- ------------------------------------------------------------
-- Fix 4: 补种 djs_pig_type 字典（猪只类型）
--   t_farm_pig_info.pig_type 引用此字典
--   dict_id = 100109，数据 ID 从 1001100 起
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100109, '1001', '猪只类型', 'djs_pig_type', NULL, NOW(), '养殖：t_farm_pig_info.pig_type');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001100, '1001', 0, '母猪',   'sow',       'djs_pig_type', '', 'success', 'Y', NULL, NOW()),
  (1001101, '1001', 1, '公猪',   'boar',      'djs_pig_type', '', 'primary', 'N', NULL, NOW()),
  (1001102, '1001', 2, '仔猪',   'piglet',    'djs_pig_type', '', 'info',    'N', NULL, NOW()),
  (1001103, '1001', 3, '育肥猪', 'fattening', 'djs_pig_type', '', 'warning', 'N', NULL, NOW());

-- ------------------------------------------------------------
-- Fix 5: 补种 djs_pig_end_reason 字典（终止原因）
--   t_farm_pig_info.end_reason 引用此字典
--   dict_id = 100110，数据 ID 从 1001110 起
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100110, '1001', '终止原因', 'djs_pig_end_reason', NULL, NOW(), '养殖：t_farm_pig_info.end_reason（DIE→DEAD / ELIMINATE→CULL / SLAUGHTER→MARKET）');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001110, '1001', 0, '死亡', 'DEAD',   'djs_pig_end_reason', '', 'danger',  'N', NULL, NOW()),
  (1001111, '1001', 1, '淘汰', 'CULL',   'djs_pig_end_reason', '', 'warning', 'N', NULL, NOW()),
  (1001112, '1001', 2, '出栏', 'MARKET', 'djs_pig_end_reason', '', 'info',    'N', NULL, NOW());
