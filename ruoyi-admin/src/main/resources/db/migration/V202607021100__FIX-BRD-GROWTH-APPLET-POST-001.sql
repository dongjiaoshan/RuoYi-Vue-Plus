-- FIX-BRD-GROWTH-APPLET-POST-001 — mp 生长记录「录入」applet 权限菜单 seed
--
-- 背景：mp 端「生长记录」录入（提交体测）原调用 admin 端
--        POST /djs/breed/event/growth（权限 djs:breed:event:growth:add）。mp 角色从未
--        被授该 admin 权限（V202606221000 只批量授 djs:applet:*，不覆盖 admin 权限），
--        真微信登录后提交体测一律 403，功能不可用。
--      本 ticket：GrowthAppletController 新增 POST /djs/applet/breed/growth（权限
--        djs:applet:breed:growth:add），新增独立 applet 权限并授给全部业务角色
--        （沿 V202606221000 DJS-FIX-APPLET-PERMS-001 范式：非 superadmin 全量补授）。
--
-- menu_id 段 5357：接 V202606111700 的 5356（djs:applet:breed:growth:list）后，
-- 落在 5350-5360 mp picker / applet 子段（CLAUDE.md §6 系统底座 5000-5999）。

-- ============================================================
-- 1. mp 生长记录录入权限菜单（visible='1' 隐藏；F 按钮型，parent_id=0 独立挂 root）
-- ============================================================
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (5357, 'mp 生长记录录入', 0, 98, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:applet:breed:growth:add', '#', 1, NOW(),
   'FIX-BRD-GROWTH-APPLET-POST-001 mp 提交体测 POST /djs/applet/breed/growth');

-- ============================================================
-- 2. 授权给所有非 superadmin 业务角色（沿 V202606221000 范式；INSERT IGNORE 幂等）
-- ============================================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 5357 AS menu_id) m
WHERE r.role_id <> 1
  AND r.del_flag = '0';
