-- DJS-FIX-MP-W22-002 采食原料（饲料）领用 mp 应用权限菜单 seed
--
-- 背景：mp 端 breed 首页"采食原料领用"卡片原本错路由到药品领用页
-- （单位"盒/瓶"，关联 t_breed_medicine_*）。本 hotfix 新建饲料专属 mp 页
-- pages/breed/feed/index.vue + 后端 AppletFeedController（2 个只读端点），
-- 需配套 djs:applet:breed:feed:list 权限菜单。
--
-- menu_id 段 7361-7362（CLAUDE.md §6 #6 养殖域 7000-7999；7360 mp 猪只 picker
-- 已用，本 ticket 占 7361-7362 子段，给饲料 mp 入口）。
--
-- 端点：
--   GET /djs/applet/breed/feed/stock      聚合饲料类产品全场库存
--   GET /djs/applet/breed/feed/issue-log  当前登录人饲料领用流水
--
-- 录入端（pick / return / loss）仍复用 AppletMatFlowController + matType=feed，
-- D10 WMS-MAT-001-FEED-EXT 接通 mp 真录入，不在本 ticket。

-- ============================================================
-- 1. 饲料列表权限菜单（visible='1' 隐藏，走 F 按钮型 perms-only）
--    parent_id=0 让权限独立挂到 root，避免污染系统菜单树，给 sa-token 校验 perms 走非 admin role 用
-- ============================================================
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7361, 'mp 饲料原料领用', 0, 21, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:applet:breed:feed:list', '#', 1, NOW(),
   'DJS-FIX-MP-W22-002 AppletFeedController GET stock / issue-log');

-- ============================================================
-- 2. 挂权限给 mp 真实使用角色（admin role_id=1 已含全权限不动）
--    实际 mp 登录角色：breed_worker / vet / plant_worker / warehouse_worker /
--    store_clerk / breed_admin / plant_admin / warehouse_admin / store_admin /
--    boss / manager。本 ticket 给所有"非超管 + 非纯系统管理"角色挂上
--    饲料列表读权限（与 djs:applet:warehouse:product:list / mat:* 同语义层）。
--
--    备注：MP-UX-002 / D9-closing-applet-farrow-menu 原范式 LIKE %mp/applet/staff/
--    employee% 在本库 role_key 命名下不命中（实际命名是 *_worker / *_admin /
--    vet / boss / manager），导致历史 5350-5355 picker 权限对所有 mp 用户都 403。
--    本 ticket 改用 role_id 白名单（排除 1 超管 + 101 system_admin），与现状对齐
--    并修正语义。
-- ============================================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 7361 AS menu_id
FROM sys_role r
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0';
