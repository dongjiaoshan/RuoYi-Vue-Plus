-- BRD-FIX-MP-EVENT-MISC-IA-001 — mp 生长记录"记录列表"applet 权限菜单 seed
--
-- 背景：mp 端"生长记录"页第 3 段「记录列表」（原型 15 timeline）需要一个 mp applet 列表端点：
--        GET /djs/applet/breed/growth/list?earNo=&pigId=
--      mp_role 默认不含 admin 端 djs:breed:event:growth:* 权限，本 ticket 新增独立
--      djs:applet:breed:growth:list 权限并挂给所有 mp / staff / employee 角色
--      （沿 V202606071540 D9-closing-applet-farrow-menu 范式）。
--
-- 注：仔猪耳号「选窝」两端点（pending-litters / pending-barn-count）复用既有
--    djs:applet:breed:farrow:list（5353）权限，无需新 seed。
--
-- menu_id 段 5356：接 D9-closing 5353-5355 段后，落在 5350-5360 mp picker / applet 子段
-- （CLAUDE.md §6 系统底座 5000-5999；MIN-INFRA-001 5300-5399 给 mp 权限菜单）。

-- ============================================================
-- 1. mp 生长记录列表权限菜单（visible='1' 隐藏；F 按钮型，parent_id=0 独立挂 root）
-- ============================================================
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (5356, 'mp 生长记录列表', 0, 97, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:applet:breed:growth:list', '#', 1, NOW(),
   'BRD-FIX-MP-EVENT-MISC-IA-001 mp 生长记录 timeline GET /djs/applet/breed/growth/list');

-- ============================================================
-- 2. 挂权限给 mp / staff / employee 角色（admin role_id=1 已含全权限，不动）
-- ============================================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 5356 AS menu_id) m
WHERE r.role_id <> 1
  AND r.del_flag = '0'
  AND (r.role_key LIKE '%mp%'
       OR r.role_key LIKE '%applet%'
       OR r.role_key LIKE '%staff%'
       OR r.role_key LIKE '%employee%'
       OR r.role_name LIKE '%员工%'
       OR r.role_name LIKE '%小程序%');
