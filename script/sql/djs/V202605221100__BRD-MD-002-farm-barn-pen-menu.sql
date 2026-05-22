-- ============================================================
-- BRD-MD-002 农场 + 栋舍 + 栏位 菜单 seed
--   表 sys_farm / t_farm_barn_info / t_farm_barn_pen 已在 SYS-INIT-001 cleanup 阶段建好
--   字典 djs_barn_type / djs_pen_type / djs_farm_status 已在 SYS-AUTH-001 seed
--   本文件只 seed 三块菜单（养殖目录 7000 下）：
--     7020-7022  农场信息（query / edit）        路由 djs-breed/farm/index  权限 djs:breed:farm-info:*
--     7030-7034  栋舍管理（list/add/edit/remove）              权限 djs:breed:barn:*
--     7040-7044  栏位管理（list/add/edit/remove）              权限 djs:breed:pen:*
--   注：栋舍 + 栏位 + 农场详情共用同一前端单页 djs-breed/farm/index.vue
--       （左侧 el-tree 栋舍→栏位，右侧 panel；农场只读 panel 在顶部）
--       菜单层"农场信息"挂目录，其下 5+5+2 = 12 个按钮权限串
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 二级目录：农场信息（4 tab/section 单页）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7020, '农场信息', 7000, 2, 'farm', 'djs-breed/farm/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:farm-info:query', 'building', 1, NOW(), 'BRD-MD-002');

-- ------------------------------------------------------------
-- 2. 农场信息按钮权限（2 个：query / edit）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7021, '农场查询', 7020, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:farm-info:query', '#', 1, NOW(), 'BRD-MD-002'),
  (7022, '农场联系信息编辑', 7020, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:farm-info:edit',  '#', 1, NOW(), 'BRD-MD-002');

-- ------------------------------------------------------------
-- 3. 栋舍按钮权限（4 个：list/add/edit/remove；与后端 @SaCheckPermission 严格一致）
--    挂在 7020 农场信息目录下（同一单页操作）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7030, '栋舍查询', 7020, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:barn:list',   '#', 1, NOW(), 'BRD-MD-002'),
  (7031, '栋舍新增', 7020, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:barn:add',    '#', 1, NOW(), 'BRD-MD-002'),
  (7032, '栋舍修改', 7020, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:barn:edit',   '#', 1, NOW(), 'BRD-MD-002'),
  (7033, '栋舍删除', 7020, 6, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:barn:remove', '#', 1, NOW(), 'BRD-MD-002');

-- ------------------------------------------------------------
-- 4. 栏位按钮权限（4 个）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7040, '栏位查询', 7020, 7, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pen:list',   '#', 1, NOW(), 'BRD-MD-002'),
  (7041, '栏位新增', 7020, 8, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pen:add',    '#', 1, NOW(), 'BRD-MD-002'),
  (7042, '栏位修改', 7020, 9, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pen:edit',   '#', 1, NOW(), 'BRD-MD-002'),
  (7043, '栏位删除', 7020, 10, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pen:remove', '#', 1, NOW(), 'BRD-MD-002');
