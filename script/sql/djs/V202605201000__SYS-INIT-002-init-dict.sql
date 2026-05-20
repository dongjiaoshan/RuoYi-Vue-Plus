-- ============================================================
-- SYS-INIT-002 字典数据初始化
-- 生成时间: 2026-05-20
-- 范围: 38 类 dict_type + 约 224 条 dict_data
--   A 系统通用 6 类 (dict_id 100000-100099, dict_code 1000000-1000999)
--   B 养殖域   8 类 (dict_id 100100-100199, dict_code 1001000-1001999)
--   C 种植域   5 类 (dict_id 100200-100249, dict_code 1002000-1002499)
--   D 种植空白 8 类 (dict_id 100250-100299, dict_code 1002500-1002999)  -- doc/02 v1.1 + doc/06 要求
--   E 仓库域   6 类 (dict_id 100300-100399, dict_code 1003000-1003999)
--   F 门店域   3 类 (dict_id 100400-100499, dict_code 1004000-1004999)
--   G 跨域/追溯 2 类 (dict_id 100500-100599, dict_code 1005000-1005999)
-- 约束:
--   1. 全部 INSERT IGNORE 幂等（PK / UNIQUE(tenant_id, dict_type) 撞了即跳过）
--   2. tenant_id 全 '1001'（与 SYS-INIT-001 CR-20260520-01 一致，VARCHAR(20)）
--   3. dict_type 前缀 'djs_'
--   4. dict_label 中文 / dict_value 英文蛇形或大写枚举
--   5. create_by / create_dept 为 bigint，置 NULL（ruoyi 5.x 设计：业务字典无具体创建人）
-- 引用: doc/02-需求拆解-v1.2.md §SYS-INIT-002, doc/06-实现描述.md §SYS-INIT-002
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- A. 系统通用（6 类）
-- ============================================================

-- A1 djs_user_status 用户状态
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100001, '1001', '用户状态', 'djs_user_status', NULL, NOW(), '系统通用：员工档案状态');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1000010, '1001', 0, '在职', '0', 'djs_user_status', '', 'primary', 'Y', NULL, NOW()),
  (1000011, '1001', 1, '离职', '1', 'djs_user_status', '', 'danger',  'N', NULL, NOW()),
  (1000012, '1001', 2, '试用', '2', 'djs_user_status', '', 'warning', 'N', NULL, NOW());

-- A2 djs_role_code 系统角色（13 个，v1.2 含 boss + manager）
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100002, '1001', '系统角色', 'djs_role_code', NULL, NOW(), '系统通用：13 种业务角色 code');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1000020, '1001',  0, '老板',           'boss',             'djs_role_code', '', 'primary', 'N', NULL, NOW()),
  (1000021, '1001',  1, '管理人员',       'manager',          'djs_role_code', '', 'primary', 'N', NULL, NOW()),
  (1000022, '1001',  2, '系统管理员',     'admin',            'djs_role_code', '', 'danger',  'N', NULL, NOW()),
  (1000023, '1001',  3, '养猪员',         'pig_keeper',       'djs_role_code', '', 'info',    'N', NULL, NOW()),
  (1000024, '1001',  4, '种植员',         'planter',          'djs_role_code', '', 'info',    'N', NULL, NOW()),
  (1000025, '1001',  5, '仓管员',         'warehouse_keeper', 'djs_role_code', '', 'info',    'N', NULL, NOW()),
  (1000026, '1001',  6, '门店员工',       'store_clerk',      'djs_role_code', '', 'info',    'N', NULL, NOW()),
  (1000027, '1001',  7, '店长',           'store_manager',    'djs_role_code', '', 'success', 'N', NULL, NOW()),
  (1000028, '1001',  8, '调度员',         'dispatcher',       'djs_role_code', '', 'warning', 'N', NULL, NOW()),
  (1000029, '1001',  9, '屠宰员',         'butcher',          'djs_role_code', '', 'info',    'N', NULL, NOW()),
  (1000030, '1001', 10, '打包员',         'packer',           'djs_role_code', '', 'info',    'N', NULL, NOW()),
  (1000031, '1001', 11, '司机',           'driver',           'djs_role_code', '', 'info',    'N', NULL, NOW()),
  (1000032, '1001', 12, '顾客',           'customer',         'djs_role_code', '', '',        'N', NULL, NOW());

-- A3 djs_farm_status 农场状态
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100003, '1001', '农场状态', 'djs_farm_status', NULL, NOW(), '系统通用：sys_farm.farm_status');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1000040, '1001', 0, '启用', '0', 'djs_farm_status', '', 'primary', 'Y', NULL, NOW()),
  (1000041, '1001', 1, '停用', '1', 'djs_farm_status', '', 'danger',  'N', NULL, NOW());

-- A4 djs_store_status 门店状态
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100004, '1001', '门店状态', 'djs_store_status', NULL, NOW(), '系统通用：t_md_store.status');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1000050, '1001', 0, '启用',   '0', 'djs_store_status', '', 'primary', 'Y', NULL, NOW()),
  (1000051, '1001', 1, '停用',   '1', 'djs_store_status', '', 'danger',  'N', NULL, NOW()),
  (1000052, '1001', 2, '装修中', '2', 'djs_store_status', '', 'warning', 'N', NULL, NOW());

-- A5 djs_supplier_type 供应商类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100005, '1001', '供应商类型', 'djs_supplier_type', NULL, NOW(), '系统通用：t_md_supplier.supplier_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1000060, '1001', 0, '饲料',     'feed',  'djs_supplier_type', '', 'primary', 'N', NULL, NOW()),
  (1000061, '1001', 1, '种猪',     'breed', 'djs_supplier_type', '', 'primary', 'N', NULL, NOW()),
  (1000062, '1001', 2, '兽药',     'med',   'djs_supplier_type', '', 'warning', 'N', NULL, NOW()),
  (1000063, '1001', 3, '蔬菜种子', 'seed',  'djs_supplier_type', '', 'success', 'N', NULL, NOW()),
  (1000064, '1001', 4, '包材',     'pack',  'djs_supplier_type', '', 'info',    'N', NULL, NOW()),
  (1000065, '1001', 5, '其他',     'other', 'djs_supplier_type', '', '',        'N', NULL, NOW());

-- A6 djs_yes_no 通用是否
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100006, '1001', '通用是否', 'djs_yes_no', NULL, NOW(), '系统通用：1=是 / 0=否');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1000070, '1001', 0, '是', '1', 'djs_yes_no', '', 'primary', 'N', NULL, NOW()),
  (1000071, '1001', 1, '否', '0', 'djs_yes_no', '', 'info',    'Y', NULL, NOW());

-- ============================================================
-- B. 养殖域（8 类）
-- ============================================================

-- B1 djs_pig_gender 猪只性别
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100101, '1001', '猪只性别', 'djs_pig_gender', NULL, NOW(), '养殖：公/母');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001010, '1001', 0, '公', 'male',   'djs_pig_gender', '', 'primary', 'N', NULL, NOW()),
  (1001011, '1001', 1, '母', 'female', 'djs_pig_gender', '', 'success', 'N', NULL, NOW());

-- B2 djs_pig_breed 品种
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100102, '1001', '猪只品种', 'djs_pig_breed', NULL, NOW(), '养殖：杜洛克/长白/大白 等');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001020, '1001', 0, '杜洛克', 'duroc',     'djs_pig_breed', '', 'primary', 'N', NULL, NOW()),
  (1001021, '1001', 1, '长白',   'landrace',  'djs_pig_breed', '', 'primary', 'N', NULL, NOW()),
  (1001022, '1001', 2, '大白',   'yorkshire', 'djs_pig_breed', '', 'primary', 'N', NULL, NOW()),
  (1001023, '1001', 3, 'PIC',    'pic',       'djs_pig_breed', '', 'success', 'N', NULL, NOW()),
  (1001024, '1001', 4, '二元',   'binary',    'djs_pig_breed', '', 'info',    'N', NULL, NOW()),
  (1001025, '1001', 5, '三元',   'ternary',   'djs_pig_breed', '', 'info',    'N', NULL, NOW()),
  (1001026, '1001', 6, '其他',   'other',     'djs_pig_breed', '', '',        'N', NULL, NOW());

-- B3 djs_pig_lifecycle 猪只生命周期阶段（状态机 9 状态 — 与 BRD-CORE-001 enum 必须严格一致）
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100103, '1001', '猪只生命周期', 'djs_pig_lifecycle', NULL, NOW(), '养殖：状态机 9 状态，BRD-CORE-001 enum 严格对齐');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001030, '1001', 0, '配种',    'BREEDING',            'djs_pig_lifecycle', '', 'primary', 'N', NULL, NOW()),
  (1001031, '1001', 1, '妊娠',    'PREGNANT',            'djs_pig_lifecycle', '', 'primary', 'N', NULL, NOW()),
  (1001032, '1001', 2, '哺乳',    'NURSING',             'djs_pig_lifecycle', '', 'success', 'N', NULL, NOW()),
  (1001033, '1001', 3, '断奶',    'WEANED',              'djs_pig_lifecycle', '', 'success', 'N', NULL, NOW()),
  (1001034, '1001', 4, '育肥',    'FATTENING',           'djs_pig_lifecycle', '', 'warning', 'N', NULL, NOW()),
  (1001035, '1001', 5, '候宰',    'READY_TO_SLAUGHTER',  'djs_pig_lifecycle', '', 'warning', 'N', NULL, NOW()),
  (1001036, '1001', 6, '已出栏',  'SLAUGHTERED',         'djs_pig_lifecycle', '', 'info',    'N', NULL, NOW()),
  (1001037, '1001', 7, '死亡',    'DEAD',                'djs_pig_lifecycle', '', 'danger',  'N', NULL, NOW()),
  (1001038, '1001', 8, '淘汰',    'ELIMINATED',          'djs_pig_lifecycle', '', 'danger',  'N', NULL, NOW());

-- B4 djs_pig_status_event 猪只状态机事件（11 个，BRD-CORE-001 严格对齐）
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100104, '1001', '状态机事件', 'djs_pig_status_event', NULL, NOW(), '养殖：状态机 11 事件，BRD-CORE-001 enum 严格对齐');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001040, '1001',  0, '引种',     'INTRO',       'djs_pig_status_event', '', 'primary', 'N', NULL, NOW()),
  (1001041, '1001',  1, '配种',     'BREED',       'djs_pig_status_event', '', 'primary', 'N', NULL, NOW()),
  (1001042, '1001',  2, '分娩',     'FARROW',      'djs_pig_status_event', '', 'success', 'N', NULL, NOW()),
  (1001043, '1001',  3, '断奶',     'WEAN',        'djs_pig_status_event', '', 'success', 'N', NULL, NOW()),
  (1001044, '1001',  4, '查情',     'OESTRUS',     'djs_pig_status_event', '', 'info',    'N', NULL, NOW()),
  (1001045, '1001',  5, '返空',     'NULL_RETURN', 'djs_pig_status_event', '', 'warning', 'N', NULL, NOW()),
  (1001046, '1001',  6, '死亡',     'DIE',         'djs_pig_status_event', '', 'danger',  'N', NULL, NOW()),
  (1001047, '1001',  7, '淘汰',     'ELIMINATE',   'djs_pig_status_event', '', 'danger',  'N', NULL, NOW()),
  (1001048, '1001',  8, '阉割',     'CASTRATE',    'djs_pig_status_event', '', 'info',    'N', NULL, NOW()),
  (1001049, '1001',  9, '转移',     'TRANSFER',    'djs_pig_status_event', '', 'info',    'N', NULL, NOW()),
  (1001050, '1001', 10, '出栏',     'SLAUGHTER',   'djs_pig_status_event', '', 'warning', 'N', NULL, NOW());

-- B5 djs_barn_type 栋舍类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100105, '1001', '栋舍类型', 'djs_barn_type', NULL, NOW(), '养殖：t_farm_barn.barn_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001060, '1001', 0, '配种舍', 'breeding',  'djs_barn_type', '', 'primary', 'N', NULL, NOW()),
  (1001061, '1001', 1, '妊娠舍', 'pregnant',  'djs_barn_type', '', 'primary', 'N', NULL, NOW()),
  (1001062, '1001', 2, '产房',   'farrow',    'djs_barn_type', '', 'success', 'N', NULL, NOW()),
  (1001063, '1001', 3, '保育舍', 'nursery',   'djs_barn_type', '', 'success', 'N', NULL, NOW()),
  (1001064, '1001', 4, '育肥舍', 'fattening', 'djs_barn_type', '', 'warning', 'N', NULL, NOW()),
  (1001065, '1001', 5, '隔离舍', 'isolation', 'djs_barn_type', '', 'danger',  'N', NULL, NOW());

-- B6 djs_pen_type 栏位类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100106, '1001', '栏位类型', 'djs_pen_type', NULL, NOW(), '养殖：t_farm_pen.pen_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001070, '1001', 0, '公栏',   'male',   'djs_pen_type', '', 'primary', 'N', NULL, NOW()),
  (1001071, '1001', 1, '母栏',   'female', 'djs_pen_type', '', 'success', 'N', NULL, NOW()),
  (1001072, '1001', 2, '限位栏', 'stall',  'djs_pen_type', '', 'warning', 'N', NULL, NOW()),
  (1001073, '1001', 3, '群栏',   'group',  'djs_pen_type', '', 'info',    'N', NULL, NOW());

-- B7 djs_med_type 药品类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100107, '1001', '药品类型', 'djs_med_type', NULL, NOW(), '养殖：t_farm_med.med_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001080, '1001', 0, '抗生素',   'antibiotic',   'djs_med_type', '', 'danger',  'N', NULL, NOW()),
  (1001081, '1001', 1, '疫苗',     'vaccine',      'djs_med_type', '', 'primary', 'N', NULL, NOW()),
  (1001082, '1001', 2, '营养剂',   'nutrition',    'djs_med_type', '', 'success', 'N', NULL, NOW()),
  (1001083, '1001', 3, '消毒剂',   'disinfectant', 'djs_med_type', '', 'warning', 'N', NULL, NOW()),
  (1001084, '1001', 4, '其他',     'other',        'djs_med_type', '', '',        'N', NULL, NOW());

-- B8 djs_elimination_reason 淘汰原因
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100108, '1001', '淘汰原因', 'djs_elimination_reason', NULL, NOW(), '养殖：淘汰事件原因分类');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001090, '1001', 0, '年龄', 'age',         'djs_elimination_reason', '', 'info',    'N', NULL, NOW()),
  (1001091, '1001', 1, '疾病', 'disease',     'djs_elimination_reason', '', 'danger',  'N', NULL, NOW()),
  (1001092, '1001', 2, '性能', 'performance', 'djs_elimination_reason', '', 'warning', 'N', NULL, NOW()),
  (1001093, '1001', 3, '其他', 'other',       'djs_elimination_reason', '', '',        'N', NULL, NOW());

-- ============================================================
-- C. 种植域（5 类 — 已有清晰值）
-- ============================================================

-- C1 djs_plot_type 地块类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100201, '1001', '地块类型', 'djs_plot_type', NULL, NOW(), '种植：t_plant_plot.plot_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002010, '1001', 0, '大棚', 'greenhouse', 'djs_plot_type', '', 'primary', 'N', NULL, NOW()),
  (1002011, '1001', 1, '露天', 'open',       'djs_plot_type', '', 'success', 'N', NULL, NOW()),
  (1002012, '1001', 2, '水田', 'paddy',      'djs_plot_type', '', 'info',    'N', NULL, NOW());

-- C2 djs_crop_type 作物类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100202, '1001', '作物类型', 'djs_crop_type', NULL, NOW(), '种植：t_plant_crop.crop_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002020, '1001', 0, '叶菜',   'leaf',       'djs_crop_type', '', 'success', 'N', NULL, NOW()),
  (1002021, '1001', 1, '根茎',   'root',       'djs_crop_type', '', 'warning', 'N', NULL, NOW()),
  (1002022, '1001', 2, '茄果',   'fruit_veg',  'djs_crop_type', '', 'primary', 'N', NULL, NOW()),
  (1002023, '1001', 3, '水果',   'fruit',      'djs_crop_type', '', 'danger',  'N', NULL, NOW()),
  (1002024, '1001', 4, '其他',   'other',      'djs_crop_type', '', '',        'N', NULL, NOW());

-- C3 djs_organic_cert_status 有机认证状态
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100203, '1001', '有机认证状态', 'djs_organic_cert_status', NULL, NOW(), '种植：t_plant_plot.organic_cert_status');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002030, '1001', 0, '未认证',   'none',       'djs_organic_cert_status', '', 'info',    'Y', NULL, NOW()),
  (1002031, '1001', 1, '转换期',   'transition', 'djs_organic_cert_status', '', 'warning', 'N', NULL, NOW()),
  (1002032, '1001', 2, '已认证',   'certified',  'djs_organic_cert_status', '', 'success', 'N', NULL, NOW()),
  (1002033, '1001', 3, '已过期',   'expired',    'djs_organic_cert_status', '', 'danger',  'N', NULL, NOW());

-- C4 djs_farm_work_type 农事类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100204, '1001', '农事类型', 'djs_farm_work_type', NULL, NOW(), '种植：t_plant_work.work_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002040, '1001',  0, '播种',   'sow',        'djs_farm_work_type', '', 'primary', 'N', NULL, NOW()),
  (1002041, '1001',  1, '移栽',   'transplant', 'djs_farm_work_type', '', 'primary', 'N', NULL, NOW()),
  (1002042, '1001',  2, '施肥',   'fertilize',  'djs_farm_work_type', '', 'success', 'N', NULL, NOW()),
  (1002043, '1001',  3, '灌溉',   'irrigate',   'djs_farm_work_type', '', 'info',    'N', NULL, NOW()),
  (1002044, '1001',  4, '喷药',   'spray',      'djs_farm_work_type', '', 'warning', 'N', NULL, NOW()),
  (1002045, '1001',  5, '除草',   'weed',       'djs_farm_work_type', '', 'warning', 'N', NULL, NOW()),
  (1002046, '1001',  6, '整地',   'till',       'djs_farm_work_type', '', 'info',    'N', NULL, NOW()),
  (1002047, '1001',  7, '修剪',   'prune',      'djs_farm_work_type', '', 'info',    'N', NULL, NOW()),
  (1002048, '1001',  8, '嫁接',   'graft',      'djs_farm_work_type', '', 'info',    'N', NULL, NOW()),
  (1002049, '1001',  9, '套袋',   'bag',        'djs_farm_work_type', '', 'info',    'N', NULL, NOW()),
  (1002050, '1001', 10, '疏果',   'thin_fruit', 'djs_farm_work_type', '', 'info',    'N', NULL, NOW()),
  (1002051, '1001', 11, '其他',   'other',      'djs_farm_work_type', '', '',        'N', NULL, NOW());

-- C5 djs_disaster_type 灾害类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100205, '1001', '灾害类型', 'djs_disaster_type', NULL, NOW(), '种植：t_plant_disaster.disaster_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002060, '1001', 0, '旱',       'drought', 'djs_disaster_type', '', 'warning', 'N', NULL, NOW()),
  (1002061, '1001', 1, '涝',       'flood',   'djs_disaster_type', '', 'primary', 'N', NULL, NOW()),
  (1002062, '1001', 2, '风',       'wind',    'djs_disaster_type', '', 'info',    'N', NULL, NOW()),
  (1002063, '1001', 3, '冻',       'frost',   'djs_disaster_type', '', 'info',    'N', NULL, NOW()),
  (1002064, '1001', 4, '病虫害',   'pest',    'djs_disaster_type', '', 'danger',  'N', NULL, NOW());

-- ============================================================
-- D. 种植空白补全（8 类 — 业内通用默认值，doc/02 v1.1 + doc/06 要求）
-- 注: 客户上线前必须过一遍（命名/术语可能有客户偏好）
-- 命名沿用 doc/02 / doc/06 既有约定，不加 djs_ 前缀冲突的话补 djs_ 统一
-- ============================================================

-- D1 djs_soil_type 土壤类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100251, '1001', '土壤类型', 'djs_soil_type', NULL, NOW(), '种植：v1.1 业内默认值占位，客户上线前确认');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002510, '1001', 0, '壤土',     'loam',        'djs_soil_type', '', 'primary', 'N', NULL, NOW()),
  (1002511, '1001', 1, '砂土',     'sand',        'djs_soil_type', '', 'warning', 'N', NULL, NOW()),
  (1002512, '1001', 2, '黏土',     'clay',        'djs_soil_type', '', 'info',    'N', NULL, NOW()),
  (1002513, '1001', 3, '壤砂土',   'loam_sand',   'djs_soil_type', '', 'primary', 'N', NULL, NOW()),
  (1002514, '1001', 4, '砂壤土',   'sand_loam',   'djs_soil_type', '', 'primary', 'N', NULL, NOW()),
  (1002515, '1001', 5, '黑土',     'black_soil',  'djs_soil_type', '', 'success', 'N', NULL, NOW());

-- D2 djs_soil_fertility 土壤肥力
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100252, '1001', '土壤肥力', 'djs_soil_fertility', NULL, NOW(), '种植：v1.1 业内默认值占位，客户上线前确认');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002520, '1001', 0, '高',     'high',   'djs_soil_fertility', '', 'success', 'N', NULL, NOW()),
  (1002521, '1001', 1, '中',     'medium', 'djs_soil_fertility', '', 'primary', 'N', NULL, NOW()),
  (1002522, '1001', 2, '低',     'low',    'djs_soil_fertility', '', 'warning', 'N', NULL, NOW()),
  (1002523, '1001', 3, '贫瘠',   'barren', 'djs_soil_fertility', '', 'danger',  'N', NULL, NOW());

-- D3 djs_terrain_condition 地势情况
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100253, '1001', '地势情况', 'djs_terrain_condition', NULL, NOW(), '种植：v1.1 业内默认值占位，客户上线前确认');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002530, '1001', 0, '平地',   'flat',         'djs_terrain_condition', '', 'success', 'N', NULL, NOW()),
  (1002531, '1001', 1, '缓坡',   'gentle_slope', 'djs_terrain_condition', '', 'primary', 'N', NULL, NOW()),
  (1002532, '1001', 2, '陡坡',   'steep_slope',  'djs_terrain_condition', '', 'warning', 'N', NULL, NOW()),
  (1002533, '1001', 3, '梯田',   'terrace',      'djs_terrain_condition', '', 'info',    'N', NULL, NOW());

-- D4 djs_light_condition 光照条件
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100254, '1001', '光照条件', 'djs_light_condition', NULL, NOW(), '种植：v1.1 业内默认值占位，客户上线前确认');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002540, '1001', 0, '充足',     'sufficient', 'djs_light_condition', '', 'success', 'N', NULL, NOW()),
  (1002541, '1001', 1, '一般',     'normal',     'djs_light_condition', '', 'primary', 'N', NULL, NOW()),
  (1002542, '1001', 2, '半阴',     'half_shade', 'djs_light_condition', '', 'warning', 'N', NULL, NOW()),
  (1002543, '1001', 3, '阴',       'shade',      'djs_light_condition', '', 'info',    'N', NULL, NOW());

-- D5 djs_drain_condition 排水条件
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100255, '1001', '排水条件', 'djs_drain_condition', NULL, NOW(), '种植：v1.1 业内默认值占位，客户上线前确认');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002550, '1001', 0, '良好', 'good',   'djs_drain_condition', '', 'success', 'N', NULL, NOW()),
  (1002551, '1001', 1, '一般', 'normal', 'djs_drain_condition', '', 'primary', 'N', NULL, NOW()),
  (1002552, '1001', 2, '较差', 'poor',   'djs_drain_condition', '', 'warning', 'N', NULL, NOW());

-- D6 djs_crop_family 作物科属
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100256, '1001', '作物科属', 'djs_crop_family', NULL, NOW(), '种植：v1.1 业内默认值占位，客户上线前确认');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002560, '1001', 0, '茄科',     'solanaceae',    'djs_crop_family', '', 'primary', 'N', NULL, NOW()),
  (1002561, '1001', 1, '葫芦科',   'cucurbitaceae', 'djs_crop_family', '', 'primary', 'N', NULL, NOW()),
  (1002562, '1001', 2, '十字花科', 'cruciferae',    'djs_crop_family', '', 'success', 'N', NULL, NOW()),
  (1002563, '1001', 3, '豆科',     'leguminosae',   'djs_crop_family', '', 'success', 'N', NULL, NOW()),
  (1002564, '1001', 4, '禾本科',   'gramineae',     'djs_crop_family', '', 'warning', 'N', NULL, NOW()),
  (1002565, '1001', 5, '菊科',     'compositae',    'djs_crop_family', '', 'info',    'N', NULL, NOW()),
  (1002566, '1001', 6, '百合科',   'liliaceae',     'djs_crop_family', '', 'info',    'N', NULL, NOW()),
  (1002567, '1001', 7, '旋花科',   'convolvulaceae','djs_crop_family', '', 'info',    'N', NULL, NOW());

-- D7 djs_tillage_type 整地类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100257, '1001', '整地类型', 'djs_tillage_type', NULL, NOW(), '种植：v1.1 业内默认值占位，客户上线前确认');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002570, '1001', 0, '深耕', 'deep',       'djs_tillage_type', '', 'primary', 'N', NULL, NOW()),
  (1002571, '1001', 1, '浅耕', 'shallow',    'djs_tillage_type', '', 'primary', 'N', NULL, NOW()),
  (1002572, '1001', 2, '旋耕', 'rotary',     'djs_tillage_type', '', 'success', 'N', NULL, NOW()),
  (1002573, '1001', 3, '免耕', 'no_till',    'djs_tillage_type', '', 'info',    'N', NULL, NOW());

-- D8 djs_tillage_way 整地方式
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100258, '1001', '整地方式', 'djs_tillage_way', NULL, NOW(), '种植：v1.1 业内默认值占位，客户上线前确认');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002580, '1001', 0, '机械翻耕', 'mechanical',  'djs_tillage_way', '', 'primary', 'N', NULL, NOW()),
  (1002581, '1001', 1, '人工翻耕', 'manual',      'djs_tillage_way', '', 'warning', 'N', NULL, NOW()),
  (1002582, '1001', 2, '起垄',     'ridging',     'djs_tillage_way', '', 'success', 'N', NULL, NOW()),
  (1002583, '1001', 3, '平整',     'leveling',    'djs_tillage_way', '', 'info',    'N', NULL, NOW());

-- ============================================================
-- E. 仓库域（6 类）
-- ============================================================

-- E1 djs_warehouse_type 仓库类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100301, '1001', '仓库类型', 'djs_warehouse_type', NULL, NOW(), '仓库：t_warehouse_house.warehouse_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1003010, '1001', 0, '鲜肉仓',   'fresh_meat', 'djs_warehouse_type', '', 'danger',  'N', NULL, NOW()),
  (1003011, '1001', 1, '蔬菜仓',   'veg',        'djs_warehouse_type', '', 'success', 'N', NULL, NOW()),
  (1003012, '1001', 2, '物资仓',   'material',   'djs_warehouse_type', '', 'info',    'N', NULL, NOW()),
  (1003013, '1001', 3, '包材仓',   'pack',       'djs_warehouse_type', '', 'warning', 'N', NULL, NOW());

-- E2 djs_product_status 产品状态
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100302, '1001', '产品状态', 'djs_product_status', NULL, NOW(), '仓库：产品在库/已发/已售/已退/报损');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1003020, '1001', 0, '在库', 'in_stock',  'djs_product_status', '', 'primary', 'Y', NULL, NOW()),
  (1003021, '1001', 1, '已发', 'shipped',   'djs_product_status', '', 'warning', 'N', NULL, NOW()),
  (1003022, '1001', 2, '已售', 'sold',      'djs_product_status', '', 'success', 'N', NULL, NOW()),
  (1003023, '1001', 3, '已退', 'returned',  'djs_product_status', '', 'info',    'N', NULL, NOW()),
  (1003024, '1001', 4, '报损', 'damaged',   'djs_product_status', '', 'danger',  'N', NULL, NOW());

-- E3 djs_demand_business 需求业态
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100303, '1001', '需求业态', 'djs_demand_business', NULL, NOW(), '仓库：t_warehouse_demand.business_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1003030, '1001', 0, '门店', 'store',      'djs_demand_business', '', 'primary', 'N', NULL, NOW()),
  (1003031, '1001', 1, '经销', 'distrib',    'djs_demand_business', '', 'success', 'N', NULL, NOW()),
  (1003032, '1001', 2, '团购', 'group',      'djs_demand_business', '', 'warning', 'N', NULL, NOW()),
  (1003033, '1001', 3, '加工', 'processing', 'djs_demand_business', '', 'info',    'N', NULL, NOW());

-- E4 djs_demand_status 需求状态
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100304, '1001', '需求状态', 'djs_demand_status', NULL, NOW(), '仓库：t_warehouse_demand.status 7 状态');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1003040, '1001', 0, '草稿',     'DRAFT',      'djs_demand_status', '', 'info',    'N', NULL, NOW()),
  (1003041, '1001', 1, '已提交',   'SUBMITTED',  'djs_demand_status', '', 'primary', 'N', NULL, NOW()),
  (1003042, '1001', 2, '已确认',   'CONFIRMED',  'djs_demand_status', '', 'primary', 'N', NULL, NOW()),
  (1003043, '1001', 3, '排产中',   'SCHEDULING', 'djs_demand_status', '', 'warning', 'N', NULL, NOW()),
  (1003044, '1001', 4, '部分发货', 'PARTIAL',    'djs_demand_status', '', 'warning', 'N', NULL, NOW()),
  (1003045, '1001', 5, '已完成',   'COMPLETED',  'djs_demand_status', '', 'success', 'N', NULL, NOW()),
  (1003046, '1001', 6, '已取消',   'CANCELLED',  'djs_demand_status', '', 'danger',  'N', NULL, NOW());

-- E5 djs_stock_flow_type 出入库类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100305, '1001', '出入库类型', 'djs_stock_flow_type', NULL, NOW(), '仓库：t_warehouse_stock_flow.flow_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1003050, '1001', 0, '入库',   'IN',       'djs_stock_flow_type', '', 'success', 'N', NULL, NOW()),
  (1003051, '1001', 1, '出库',   'OUT',      'djs_stock_flow_type', '', 'warning', 'N', NULL, NOW()),
  (1003052, '1001', 2, '调拨',   'TRANSFER', 'djs_stock_flow_type', '', 'primary', 'N', NULL, NOW()),
  (1003053, '1001', 3, '盘盈',   'GAIN',     'djs_stock_flow_type', '', 'info',    'N', NULL, NOW()),
  (1003054, '1001', 4, '盘亏',   'LOSS',     'djs_stock_flow_type', '', 'danger',  'N', NULL, NOW());

-- E6 djs_pack_type 包装类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100306, '1001', '包装类型', 'djs_pack_type', NULL, NOW(), '仓库：t_warehouse_product.pack_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1003060, '1001', 0, '散装',     'bulk',      'djs_pack_type', '', 'info',    'N', NULL, NOW()),
  (1003061, '1001', 1, '标准盒',   'std_box',   'djs_pack_type', '', 'primary', 'N', NULL, NOW()),
  (1003062, '1001', 2, '礼盒',     'gift_box',  'djs_pack_type', '', 'success', 'N', NULL, NOW()),
  (1003063, '1001', 3, '真空袋',   'vacuum',    'djs_pack_type', '', 'warning', 'N', NULL, NOW());

-- ============================================================
-- F. 门店域（3 类）
-- ============================================================

-- F1 djs_member_level 会员等级
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100401, '1001', '会员等级', 'djs_member_level', NULL, NOW(), '门店：t_store_member.member_level');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1004010, '1001', 0, '普通',           'normal', 'djs_member_level', '', 'info',    'Y', NULL, NOW()),
  (1004011, '1001', 1, '重要价值客户',   'vip',    'djs_member_level', '', 'danger',  'N', NULL, NOW()),
  (1004012, '1001', 2, '重要保持客户',   'keep',   'djs_member_level', '', 'warning', 'N', NULL, NOW());

-- F2 djs_return_reason 退货原因
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100402, '1001', '退货原因', 'djs_return_reason', NULL, NOW(), '门店：t_store_return.reason');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1004020, '1001', 0, '质量问题',         'quality',      'djs_return_reason', '', 'danger',  'N', NULL, NOW()),
  (1004021, '1001', 1, '客户改主意',       'mind_change',  'djs_return_reason', '', 'warning', 'N', NULL, NOW()),
  (1004022, '1001', 2, '配送问题',         'delivery',     'djs_return_reason', '', 'info',    'N', NULL, NOW()),
  (1004023, '1001', 3, '其他',             'other',        'djs_return_reason', '', '',        'N', NULL, NOW());

-- F3 djs_dispatch_priority 调度优先级
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100403, '1001', '调度优先级', 'djs_dispatch_priority', NULL, NOW(), '门店：t_store_dispatch.priority');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1004030, '1001', 0, '紧急', 'urgent', 'djs_dispatch_priority', '', 'danger',  'N', NULL, NOW()),
  (1004031, '1001', 1, '普通', 'normal', 'djs_dispatch_priority', '', 'primary', 'Y', NULL, NOW()),
  (1004032, '1001', 2, '低',   'low',    'djs_dispatch_priority', '', 'info',    'N', NULL, NOW());

-- ============================================================
-- G. 跨域 / 追溯（2 类）
-- ============================================================

-- G1 djs_trace_event_type 追溯事件类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100501, '1001', '追溯事件类型', 'djs_trace_event_type', NULL, NOW(), '追溯：t_store_trace.event_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1005010, '1001', 0, '引种',     'intro',     'djs_trace_event_type', '', 'primary', 'N', NULL, NOW()),
  (1005011, '1001', 1, '出生',     'birth',     'djs_trace_event_type', '', 'primary', 'N', NULL, NOW()),
  (1005012, '1001', 2, '配种',     'breed',     'djs_trace_event_type', '', 'primary', 'N', NULL, NOW()),
  (1005013, '1001', 3, '分娩',     'farrow',    'djs_trace_event_type', '', 'success', 'N', NULL, NOW()),
  (1005014, '1001', 4, '用药',     'medicate',  'djs_trace_event_type', '', 'warning', 'N', NULL, NOW()),
  (1005015, '1001', 5, '出栏',     'slaughter', 'djs_trace_event_type', '', 'danger',  'N', NULL, NOW()),
  (1005016, '1001', 6, '燎毛',     'singe',     'djs_trace_event_type', '', 'info',    'N', NULL, NOW()),
  (1005017, '1001', 7, '分割',     'split',     'djs_trace_event_type', '', 'info',    'N', NULL, NOW()),
  (1005018, '1001', 8, '发货',     'ship',      'djs_trace_event_type', '', 'warning', 'N', NULL, NOW()),
  (1005019, '1001', 9, '售卖',     'sell',      'djs_trace_event_type', '', 'success', 'N', NULL, NOW());

-- G2 djs_subscribe_message_type 订阅消息类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100502, '1001', '订阅消息类型', 'djs_subscribe_message_type', NULL, NOW(), '跨域：mp_subscribe_record.message_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1005020, '1001', 0, '出栏通知',     'slaughter_notice',  'djs_subscribe_message_type', '', 'danger',  'N', NULL, NOW()),
  (1005021, '1001', 1, '库存告警',     'stock_alert',       'djs_subscribe_message_type', '', 'warning', 'N', NULL, NOW()),
  (1005022, '1001', 2, '销售汇总',     'sales_summary',     'djs_subscribe_message_type', '', 'success', 'N', NULL, NOW()),
  (1005023, '1001', 3, '内测反馈',     'internal_feedback', 'djs_subscribe_message_type', '', 'info',    'N', NULL, NOW());

-- ============================================================
-- 验收: 期望 dict_type=38, dict_data≈224
-- SELECT COUNT(*) FROM sys_dict_type  WHERE dict_type LIKE 'djs_%';   -- 38
-- SELECT COUNT(*) FROM sys_dict_data  WHERE dict_type LIKE 'djs_%';   -- 224
-- ============================================================
