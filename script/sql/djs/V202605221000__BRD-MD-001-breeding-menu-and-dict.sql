-- ============================================================
-- BRD-MD-001 育种配置（品种 / 品系 / 配种关系）
--   表 t_farm_breed_info + t_farm_breed_config 已在 SYS-INIT-001 V202605200901 建好（CR-20260519-06 合表方案）
--   本文件只 seed：
--     1. 字典 djs_breed_strain_type（1=品种 / 2=品系）
--     2. 父菜单 7000 (养殖) 已在 SYS-AUTH-001 V202605201100 占位
--     3. 二级目录 7010 (育种配置) + 三级按钮权限 7011-7015
--   权限串 djs:breed:breeding:{list,add,edit,remove,export} 同时覆盖品种/品系/配种关系两个 Controller
--   （admin 单页 4 tab 用同一组权限，BreedInfoController + BreedConfigController 共用）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 字典 djs_breed_strain_type（品种/品系类型，TINYINT 1/2 与表字段 breed_strain 对齐）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type (
    dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100151, '1001', '育种类型', 'djs_breed_strain_type', 1, NOW(), '养殖：1=品种 / 2=品系（BRD-MD-001 / t_farm_breed_info.breed_strain 字段对齐）');

INSERT IGNORE INTO sys_dict_data (
    dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001510, '1001', 0, '品种', '1', 'djs_breed_strain_type', '', 'primary', 'Y', 1, NOW()),
  (1001511, '1001', 1, '品系', '2', 'djs_breed_strain_type', '', 'success', 'N', 1, NOW());

-- ------------------------------------------------------------
-- 2. 菜单：育种配置（挂养殖目录 7000 下）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 二级目录：育种配置（4 tab 单页：品种 / 品种配种 / 品系 / 品系配种）
  (7010, '育种配置', 7000, 1, 'breeding-config', 'djs-breed/breeding-config/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:breeding:list', 'tree', 1, NOW(), 'BRD-MD-001'),

  -- 三级按钮权限（5 个，与后端 @SaCheckPermission 严格一致）
  (7011, '育种查询', 7010, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:breeding:list',   '#', 1, NOW(), 'BRD-MD-001'),
  (7012, '育种新增', 7010, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:breeding:add',    '#', 1, NOW(), 'BRD-MD-001'),
  (7013, '育种修改', 7010, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:breeding:edit',   '#', 1, NOW(), 'BRD-MD-001'),
  (7014, '育种删除', 7010, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:breeding:remove', '#', 1, NOW(), 'BRD-MD-001'),
  (7015, '育种导出', 7010, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:breeding:export', '#', 1, NOW(), 'BRD-MD-001');
