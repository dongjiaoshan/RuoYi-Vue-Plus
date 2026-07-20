-- ============================================================
-- BRD-MED-002 药品领用菜单 seed
--
-- 分段说明：BRD-MED-001 占 7060-7075（药品库 7060-65 + 药品批次 7070-75）；
--          本 ticket 跟在 MED-001 之后用 7076-7079（CLAUDE.md §6 备选段，
--          与 BRD-MD-003 公猪/精液配置 7080-84 不冲突）。
-- 字典 djs_medicine_usage_type（use/return/loss）：本 ticket 在下方 INSERT IGNORE
-- seed，复用 sys_dict_data；与 ADR-0004 djs 字典命名规范一致。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 字典 djs_medicine_usage_type：use=领用 / return=退回 / loss=损耗
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (100800, '1001', '药品领用类型', 'djs_medicine_usage_type', 1, NOW(), 'BRD-MED-002');

INSERT IGNORE INTO sys_dict_data (
    dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
  (1008000, '1001', 1, '领用', 'use',    'djs_medicine_usage_type', '', 'primary', 'Y', 1, NOW(), 'BRD-MED-002'),
  (1008001, '1001', 2, '退回', 'return', 'djs_medicine_usage_type', '', 'success', 'N', 1, NOW(), 'BRD-MED-002'),
  (1008002, '1001', 3, '损耗', 'loss',   'djs_medicine_usage_type', '', 'danger',  'N', 1, NOW(), 'BRD-MED-002');

-- ------------------------------------------------------------
-- 2. 菜单 seed（父 7000=养殖；7076=领用台账 + 7077~7079 按钮权限）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 二级目录：药品领用台账
  (7076, '药品领用', 7000, 6, 'med-usage', 'djs-breed/med/usage', '',
   1, 0, 'C', '0', '0',
   'djs:breed:med-usage:list', 'edit', 1, NOW(), 'BRD-MED-002'),

  -- 子按钮权限（4 个，与后端 @SaCheckPermission 严格一致）
  (7077, '领用查询', 7076, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med-usage:list',   '#', 1, NOW(), 'BRD-MED-002'),
  (7078, '领用新增', 7076, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med-usage:add',    '#', 1, NOW(), 'BRD-MED-002'),
  (7079, '领用删除', 7076, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med-usage:remove', '#', 1, NOW(), 'BRD-MED-002');
