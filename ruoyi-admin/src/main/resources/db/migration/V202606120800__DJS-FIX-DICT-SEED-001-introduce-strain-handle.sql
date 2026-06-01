-- DJS-FIX-DICT-SEED-001 seed 3 字典：djs_introduce_type（全量 2）/ djs_pig_strain（全量 7）/ djs_handle_target（仅现有 3）
-- 三方对账：DB seed（本文件）+ DictTypeConstants.java 白名单 + script/sql/djs/_post-init.sh flush redis
-- dict_id 段：1320-1322（实测 sys_dict_type max=920603、sys_dict_data max=102452008，1320 段空闲无撞）
-- dict_code 段：13201-13223
-- ADR-0004 §2.1 命名 djs_<业务域>_<维度> / §2.2 DDL 字段类型对齐 / §2.3 seed-first 集中维护
-- ADR-0008 §2.1 djs-common 08 段（V202606120800 > 当前 flyway max V202606111820）
--
-- 码值来源：
--   1. djs_introduce_type — t_farm_pig_introduce.introduce_type VARCHAR(16) 存值 external/internal（PigIntroServiceImpl + mp 引种页权威）
--   2. djs_pig_strain     — t_farm_breed_info.breed_strain 是 TINYINT「品种/品系」类型标志（走 djs_breed_strain_type），非品系值集；
--                           且 t_farm_breed_info 当前 0 行无运行数据。故品系字典与 djs_pig_breed 同源（D10Y #7 / ADR-0004 §2.1 复用优先）。
--   3. djs_handle_target  — t_warehouse_handle_record.handle_target TINYINT，VegetableHandleServiceImpl 常量 1=入库/2=月台/3=有机饲料；
--                           doc/11 三态权威。第 4 值「零售直送」客户待确认，本文件 NOT seed。
--
-- 跑完必须刷 Redis 字典缓存（详 .claude/CLAUDE.md §5）：bash code/main/RuoYi-Vue-Plus/script/sql/djs/_post-init.sh

-- ------------------------------------------------------------
-- 1. djs_introduce_type 引种类型（external 外部 / internal 内部）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, remark)
VALUES
  (1320, '1001', '引种类型', 'djs_introduce_type', 103, 1, NOW(), '养殖：引种登记类型，t_farm_pig_introduce.introduce_type 存值 external/internal');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time)
VALUES
  (13201, '1001', 0, '外部引种', 'external', 'djs_introduce_type', '', 'warning', 'N', 103, 1, NOW()),
  (13202, '1001', 1, '内部引种', 'internal', 'djs_introduce_type', '', 'success', 'Y', 103, 1, NOW());

-- ------------------------------------------------------------
-- 2. djs_pig_strain 品系（与 djs_pig_breed 同源；t_farm_pig_introduce.pig_strain_code VARCHAR(32) 容得下）
--    breed_strain TINYINT 是「品种/品系」类型标志非值集，且 t_farm_breed_info 0 行 → 取 djs_pig_breed 同源 7 值
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, remark)
VALUES
  (1321, '1001', '品系', 'djs_pig_strain', 103, 1, NOW(), '养殖：猪只品系，与 djs_pig_breed 同源值集（D10Y #7）');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time)
VALUES
  (13211, '1001', 0, '杜洛克', 'duroc',     'djs_pig_strain', '', '', 'N', 103, 1, NOW()),
  (13212, '1001', 1, '长白',   'landrace',  'djs_pig_strain', '', '', 'N', 103, 1, NOW()),
  (13213, '1001', 2, '大白',   'yorkshire', 'djs_pig_strain', '', '', 'N', 103, 1, NOW()),
  (13214, '1001', 3, 'PIC',    'pic',       'djs_pig_strain', '', '', 'N', 103, 1, NOW()),
  (13215, '1001', 4, '二元',   'binary',    'djs_pig_strain', '', '', 'N', 103, 1, NOW()),
  (13216, '1001', 5, '三元',   'ternary',   'djs_pig_strain', '', '', 'N', 103, 1, NOW()),
  (13217, '1001', 6, '其他',   'other',     'djs_pig_strain', '', '', 'N', 103, 1, NOW());

-- ------------------------------------------------------------
-- 3. djs_handle_target 蔬菜处理去向（仅现有确定 3 值；第 4 值「零售直送」客户待确认 NOT seed）
--    code 值 '1'/'2'/'3' 对齐 t_warehouse_handle_record.handle_target TINYINT + VegetableHandleServiceImpl 常量
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, remark)
VALUES
  (1322, '1001', '处理去向', 'djs_handle_target', 106, 1, NOW(), '仓库：蔬菜处理去向，1=入库/2=月台/3=有机饲料（零售直送第4值待客户确认）');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time)
VALUES
  (13221, '1001', 0, '入库',     '1', 'djs_handle_target', '', 'primary', 'N', 106, 1, NOW()),
  (13222, '1001', 1, '月台',     '2', 'djs_handle_target', '', 'success', 'N', 106, 1, NOW()),
  (13223, '1001', 2, '有机饲料', '3', 'djs_handle_target', '', 'warning', 'N', 106, 1, NOW());
