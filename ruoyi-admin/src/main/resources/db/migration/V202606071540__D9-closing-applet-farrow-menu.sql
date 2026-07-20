-- D9 closing Group D — FarrowPicker / BarnPicker / PenPicker 应用端权限菜单 seed
--
-- 背景：mp 端新增 3 个 picker 组件（FarrowPicker / BarnPicker / PenPicker）依赖后端
--      3 个轻量 applet controller：
--        GET /djs/applet/breed/farrow/recent-by-mother
--        GET /djs/applet/breed/barn/list
--        GET /djs/applet/breed/pen/list
--      （MP-UX-003 _open-issues #9 决策 a，业务上更顺工人思维）
--
-- mp_role 默认不含 admin 端 djs:breed:event:farrow:* / djs:breed:barn:* / djs:breed:pen:*
-- 权限。本 ticket 新增独立 djs:applet:breed:* 权限并挂给所有 mp / staff / employee 角色
-- （沿 V202606061100__MP-UX-002 范式）。
--
-- menu_id 段 5353-5355：接 MP-UX-002 5350-5352 段后，落在 5350-5360 mp picker 子段
-- （CLAUDE.md §6 系统底座 5000-5999 / MIN-INFRA-001 已开 5300-5399 给 mp 权限菜单）。
--
-- 分配：
--   5353  mp 分娩 picker（perm djs:applet:breed:farrow:list）
--   5354  mp 栋舍 picker（perm djs:applet:breed:barn:list）
--   5355  mp 栏位 picker（perm djs:applet:breed:pen:list）
--   5356-5360 预留（后续 mp picker：SemenPicker / SupplierPicker / 等）

-- ============================================================
-- 1. 3 个 picker 权限菜单（visible='1' 隐藏；F 按钮型，parent_id=0 独立挂 root）
-- ============================================================
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (5353, 'mp 分娩 picker', 0, 94, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:applet:breed:farrow:list', '#', 1, NOW(),
   'D9 closing FarrowPicker GET /djs/applet/breed/farrow/recent-by-mother'),

  (5354, 'mp 栋舍 picker', 0, 95, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:applet:breed:barn:list', '#', 1, NOW(),
   'D9 closing BarnPicker GET /djs/applet/breed/barn/list'),

  (5355, 'mp 栏位 picker', 0, 96, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:applet:breed:pen:list', '#', 1, NOW(),
   'D9 closing PenPicker GET /djs/applet/breed/pen/list');

-- ============================================================
-- 2. 挂权限给 mp / staff / employee 角色（admin role_id=1 已含全权限，不动）
--    同 V202606061100 §2 范式：按 role_key / role_name 关键字模糊匹配兜底
-- ============================================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 5353 AS menu_id UNION ALL SELECT 5354 UNION ALL SELECT 5355) m
WHERE r.role_id <> 1
  AND r.del_flag = '0'
  AND (r.role_key LIKE '%mp%'
       OR r.role_key LIKE '%applet%'
       OR r.role_key LIKE '%staff%'
       OR r.role_key LIKE '%employee%'
       OR r.role_name LIKE '%员工%'
       OR r.role_name LIKE '%小程序%');
