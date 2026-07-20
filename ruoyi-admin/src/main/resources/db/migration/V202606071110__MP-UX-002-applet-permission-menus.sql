-- MP-UX-002 applet picker 权限菜单 seed
--
-- 背景：mp 端新增 3 个 picker 组件（StorePicker / LocationPicker / ProductPicker）依赖后端
--      3 个轻量 applet controller（GET /djs/applet/common/store/list /
--      /djs/applet/warehouse/location/list / /djs/applet/warehouse/product/list）。
--
-- mp_role 默认不含 admin 端 djs:common:store:list / djs:warehouse:location:list /
-- djs:warehouse:product:list 权限——本 ticket 新增独立 djs:applet:* 权限并自动挂给
-- 所有 mp / staff / employee 角色（沿 V202605300900__BRD-LIST-001-menu.sql §4-5 范式）。
--
-- menu_id 段 5350-5360：mp 应用权限菜单段（CLAUDE.md §6 分段：5000-5999 系统底座 +
-- MIN-INFRA-001 已开 5300-5399 给 mp 权限菜单；本 ticket 取 5350-5360 子段）。
--
-- 分配：
--   5350  mp 通用门店 picker（perm djs:applet:common:store:list）
--   5351  mp 库位 picker            (perm djs:applet:warehouse:location:list)
--   5352  mp 产品 picker            (perm djs:applet:warehouse:product:list)
--   5353-5360 预留（FarrowPicker / DemandPicker / 等下游 mp picker）

-- ============================================================
-- 1. 3 个 picker 权限菜单（visible='1' 隐藏；走 F 按钮型，挂在养殖父菜单下避免污染系统树）
--    parent_id=0 让权限独立挂到 root，给 sa-token 校验 perms 走非 admin role 用
-- ============================================================
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (5350, 'mp 门店 picker', 0, 91, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:applet:common:store:list', '#', 1, NOW(),
   'MP-UX-002 StorePicker GET /djs/applet/common/store/list'),

  (5351, 'mp 库位 picker', 0, 92, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:applet:warehouse:location:list', '#', 1, NOW(),
   'MP-UX-002 LocationPicker GET /djs/applet/warehouse/location/list'),

  (5352, 'mp 产品 picker', 0, 93, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:applet:warehouse:product:list', '#', 1, NOW(),
   'MP-UX-002 ProductPicker GET /djs/applet/warehouse/product/list');

-- ============================================================
-- 2. 挂权限给 mp / staff / employee 角色（admin role_id=1 已含全权限，不动）
--    同 V202605300900 §5 范式：按 role_key / role_name 关键字模糊匹配兜底
-- ============================================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 5350 AS menu_id UNION ALL SELECT 5351 UNION ALL SELECT 5352) m
WHERE r.role_id <> 1
  AND r.del_flag = '0'
  AND (r.role_key LIKE '%mp%'
       OR r.role_key LIKE '%applet%'
       OR r.role_key LIKE '%staff%'
       OR r.role_key LIKE '%employee%'
       OR r.role_name LIKE '%员工%'
       OR r.role_name LIKE '%小程序%');
