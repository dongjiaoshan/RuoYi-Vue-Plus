-- ============================================================
-- SYS-FIX-001 biz 组件字典补 seed（D03 _open-issues #37）
-- 范围: 7 类 dict_type + ~37 条 dict_data
--   dict_id 100600-100699 / dict_code 1006000-1006099（H 跨域补充段）
-- 约束:
--   1. INSERT IGNORE 幂等
--   2. tenant_id 全 '1001'
--   3. 与 doc/06 BRD-CORE-001 母猪状态机 / BRD-MED-* / WMS-DEMAND-* 命名严格对齐
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- H1 djs_sow_status 母猪状态机（7 状态 — 与 BRD-CORE-001 SowStatus enum 严格一致）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100600, '1001', '母猪状态', 'djs_sow_status', NULL, NOW(), '养殖：母猪状态机 7 状态，BRD-CORE-001 enum 严格对齐');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006000, '1001', 0, '空怀',   'EMPTY',       'djs_sow_status', '', 'info',    'Y', NULL, NOW()),
  (1006001, '1001', 1, '配种',   'BREEDING',    'djs_sow_status', '', 'primary', 'N', NULL, NOW()),
  (1006002, '1001', 2, '妊娠',   'PREGNANT',    'djs_sow_status', '', 'primary', 'N', NULL, NOW()),
  (1006003, '1001', 3, '分娩',   'FARROWING',   'djs_sow_status', '', 'success', 'N', NULL, NOW()),
  (1006004, '1001', 4, '哺乳',   'LACTATING',   'djs_sow_status', '', 'success', 'N', NULL, NOW()),
  (1006005, '1001', 5, '返情',   'RETURN_HEAT', 'djs_sow_status', '', 'warning', 'N', NULL, NOW()),
  (1006006, '1001', 6, '淘汰',   'ELIMINATED',  'djs_sow_status', '', 'danger',  'N', NULL, NOW());

-- ------------------------------------------------------------
-- H2 djs_material_type 物资类型（仓库 biz 组件 MaterialCard / MaterialList）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100601, '1001', '物资类型', 'djs_material_type', NULL, NOW(), '仓库：饲料/兽药/疫苗/工器具/其他');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006015, '1001', 0, '饲料',   'feed',    'djs_material_type', '', 'primary', 'Y', NULL, NOW()),
  (1006016, '1001', 1, '兽药',   'med',     'djs_material_type', '', 'warning', 'N', NULL, NOW()),
  (1006017, '1001', 2, '疫苗',   'vaccine', 'djs_material_type', '', 'success', 'N', NULL, NOW()),
  (1006018, '1001', 3, '工器具', 'tool',    'djs_material_type', '', 'info',    'N', NULL, NOW()),
  (1006019, '1001', 4, '其他',   'other',   'djs_material_type', '', '',        'N', NULL, NOW());

-- ------------------------------------------------------------
-- H3 djs_task_status 任务状态（TaskCard / 派工通用）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100602, '1001', '任务状态', 'djs_task_status', NULL, NOW(), '跨域：派工/作业 5 状态');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006020, '1001', 0, '待处理',  'pending',     'djs_task_status', '', 'info',    'Y', NULL, NOW()),
  (1006021, '1001', 1, '进行中',  'in_progress', 'djs_task_status', '', 'primary', 'N', NULL, NOW()),
  (1006022, '1001', 2, '已完成',  'completed',   'djs_task_status', '', 'success', 'N', NULL, NOW()),
  (1006023, '1001', 3, '已取消',  'cancelled',   'djs_task_status', '', '',        'N', NULL, NOW()),
  (1006024, '1001', 4, '已退回',  'returned',    'djs_task_status', '', 'warning', 'N', NULL, NOW());

-- ------------------------------------------------------------
-- H4 djs_task_biz 任务业务类型（TaskCard 顶部 bizType 标签）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100603, '1001', '任务业务类型', 'djs_task_biz', NULL, NOW(), '跨域：派工业务类型 6 类');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006030, '1001', 0, '养殖', 'breed',     'djs_task_biz', '', 'primary', 'Y', NULL, NOW()),
  (1006031, '1001', 1, '种植', 'plant',     'djs_task_biz', '', 'success', 'N', NULL, NOW()),
  (1006032, '1001', 2, '仓库', 'warehouse', 'djs_task_biz', '', 'info',    'N', NULL, NOW()),
  (1006033, '1001', 3, '门店', 'store',     'djs_task_biz', '', 'warning', 'N', NULL, NOW()),
  (1006034, '1001', 4, '用药', 'med',       'djs_task_biz', '', 'danger',  'N', NULL, NOW()),
  (1006035, '1001', 5, '通用', 'general',   'djs_task_biz', '', '',        'N', NULL, NOW());

-- ------------------------------------------------------------
-- H5 djs_return_loss_reason 退还/损耗原因（ReturnLossForm）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100604, '1001', '退还/损耗原因', 'djs_return_loss_reason', NULL, NOW(), '仓库：退货/损耗常见原因');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006040, '1001', 0, '货品损坏',    'damaged',         'djs_return_loss_reason', '', 'danger',  'Y', NULL, NOW()),
  (1006041, '1001', 1, '过期/失效',   'expired',         'djs_return_loss_reason', '', 'danger',  'N', NULL, NOW()),
  (1006042, '1001', 2, '规格错',     'wrong_spec',      'djs_return_loss_reason', '', 'warning', 'N', NULL, NOW()),
  (1006043, '1001', 3, '客户退还',    'customer_return', 'djs_return_loss_reason', '', 'info',    'N', NULL, NOW()),
  (1006044, '1001', 4, '盘亏',       'inventory_loss',  'djs_return_loss_reason', '', 'warning', 'N', NULL, NOW()),
  (1006045, '1001', 5, '其他',       'other',           'djs_return_loss_reason', '', '',        'N', NULL, NOW());

-- ------------------------------------------------------------
-- H6 djs_crop 作物（PlotPickList / 田块 biz 组件，与 djs_crop_type 大类区分）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100605, '1001', '作物', 'djs_crop', NULL, NOW(), '种植：常见作物明细，djs_crop_type 是大类');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006050, '1001', 0, '水稻',  'rice',      'djs_crop', '', 'success', 'Y', NULL, NOW()),
  (1006051, '1001', 1, '小麦',  'wheat',     'djs_crop', '', 'warning', 'N', NULL, NOW()),
  (1006052, '1001', 2, '玉米',  'corn',      'djs_crop', '', 'warning', 'N', NULL, NOW()),
  (1006053, '1001', 3, '大豆',  'soybean',   'djs_crop', '', 'info',    'N', NULL, NOW()),
  (1006054, '1001', 4, '蔬菜',  'vegetable', 'djs_crop', '', 'success', 'N', NULL, NOW()),
  (1006055, '1001', 5, '水果',  'fruit',     'djs_crop', '', 'danger',  'N', NULL, NOW()),
  (1006056, '1001', 6, '其他',  'other',     'djs_crop', '', '',        'N', NULL, NOW());

-- ------------------------------------------------------------
-- H7 djs_dispatch_stage 派工阶段（DispatchEntry / 仓库 biz 组件）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100606, '1001', '派工阶段', 'djs_dispatch_stage', NULL, NOW(), '仓库/跨域：派工生命周期 5 阶段');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006060, '1001', 0, '计划中',  'planning',   'djs_dispatch_stage', '', 'info',    'Y', NULL, NOW()),
  (1006061, '1001', 1, '已派工',  'dispatched', 'djs_dispatch_stage', '', 'primary', 'N', NULL, NOW()),
  (1006062, '1001', 2, '执行中',  'in_field',   'djs_dispatch_stage', '', 'primary', 'N', NULL, NOW()),
  (1006063, '1001', 3, '已完成',  'completed',  'djs_dispatch_stage', '', 'success', 'N', NULL, NOW()),
  (1006064, '1001', 4, '已回执',  'reported',   'djs_dispatch_stage', '', 'success', 'N', NULL, NOW());
