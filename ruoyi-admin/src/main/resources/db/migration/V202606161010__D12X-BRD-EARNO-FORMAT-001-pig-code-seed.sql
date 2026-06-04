-- D12X-BRD-EARNO-FORMAT-001：按客户权威耳号码表重建 djs_pig_strain（5 值）/ djs_pig_breed（6 值），dict_value 直接存位码。
-- 决策见 ADR-0011（Accepted，Kevin 2026-06-03）。
--   品系 dict_value = 第 1 位码 1-5：1=恩施黑猪/2=两头乌/3=梅花星/4=杜洛克/5=巴克夏
--   品种 dict_value = 第 2-3 位码 01-06：01=恩施黑猪/02=两头乌/03=梅花星/04=杜洛克/05=巴克夏/06=其它二元
-- 旧占位单词码（duroc/landrace/...）是错值，本文件全量替换：先 DELETE 旧 dict_data 行（dict_type 行 dict_id 不变保留），再 INSERT 新位码值。
--   （INSERT IGNORE 不会更新已存在行，必须先 DELETE 清旧单词码，否则旧值残留。）
-- dict_code 段：strain 13231-13235 / breed 13241-13246（全新空闲段，与旧 code 段及 djs_barn_type 1001060 段不撞）。
-- 现存 12 头测试猪 pig_breed_code 脏值 '1' 规范为 '01'（对齐新品种码长，让 admin 反显正常）；pig_strain_code='1' 已合法不动。
-- ADR-0004 §2.1/§2.3 / ADR-0008 breed 段（V202606161010 > 当前 flyway max V202606161000）。
-- 跑完必须刷 Redis 字典缓存（.claude/CLAUDE.md §5）：bash code/main/RuoYi-Vue-Plus/script/sql/djs/_post-init.sh

-- ------------------------------------------------------------
-- 1. djs_pig_strain 品系（重建为 5 值，dict_value = 位码 1-5）
-- ------------------------------------------------------------
DELETE FROM sys_dict_data WHERE dict_type = 'djs_pig_strain';

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time)
VALUES
  (13231, '1001', 1, '恩施黑猪', '1', 'djs_pig_strain', '', 'primary', 'N', 103, 1, NOW()),
  (13232, '1001', 2, '两头乌',   '2', 'djs_pig_strain', '', 'primary', 'N', 103, 1, NOW()),
  (13233, '1001', 3, '梅花星',   '3', 'djs_pig_strain', '', 'primary', 'N', 103, 1, NOW()),
  (13234, '1001', 4, '杜洛克',   '4', 'djs_pig_strain', '', 'success', 'N', 103, 1, NOW()),
  (13235, '1001', 5, '巴克夏',   '5', 'djs_pig_strain', '', 'success', 'N', 103, 1, NOW());

-- ------------------------------------------------------------
-- 2. djs_pig_breed 品种（重建为 6 值，dict_value = 位码 01-06）
-- ------------------------------------------------------------
DELETE FROM sys_dict_data WHERE dict_type = 'djs_pig_breed';

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time)
VALUES
  (13241, '1001', 1, '恩施黑猪', '01', 'djs_pig_breed', '', 'primary', 'N', 103, 1, NOW()),
  (13242, '1001', 2, '两头乌',   '02', 'djs_pig_breed', '', 'primary', 'N', 103, 1, NOW()),
  (13243, '1001', 3, '梅花星',   '03', 'djs_pig_breed', '', 'primary', 'N', 103, 1, NOW()),
  (13244, '1001', 4, '杜洛克',   '04', 'djs_pig_breed', '', 'success', 'N', 103, 1, NOW()),
  (13245, '1001', 5, '巴克夏',   '05', 'djs_pig_breed', '', 'success', 'N', 103, 1, NOW()),
  (13246, '1001', 6, '其它二元', '06', 'djs_pig_breed', '', 'info',    'N', 103, 1, NOW());

-- ------------------------------------------------------------
-- 3. 规范 12 头存量测试猪的 pig_breed_code 脏值 '1' → '01'（对齐新品种码长）
--    pig_strain_code='1' 已合法（恩施黑猪），不动；ear_no 旧 12 位号保留不动（ADR-0011 §2.7 新旧隔离）
-- ------------------------------------------------------------
UPDATE t_farm_pig_info      SET pig_breed_code = '01' WHERE pig_breed_code = '1';
UPDATE t_farm_pig_introduce SET pig_breed_code = '01' WHERE pig_breed_code = '1';
