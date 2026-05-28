-- DJS-FIX-MP-W22-003 mp 仔猪耳标徽标 applet 权限菜单 seed
--
-- 背景：mp breed home "仔猪耳标"卡右上角加"待贴 N 头"红色徽标。
-- 新建 GET /djs/applet/breed/eartag/farrow-pending 端点，需配套
-- djs:applet:breed:eartag 权限菜单（D5 BRD-EVENT-003 seed 的 7110-7112
-- 是 admin 端 djs:breed:event:eartag* 串，串名不同，不复用）。
--
-- menu_id 段：mp 应用权限菜单按 D9X-MP-002 范式占 7363（养殖域 7000-7999；
-- 7361 mp 饲料 / 7362 预留 已占 D9X-002；7363 给 mp 耳标徽标）。
--
-- 端点：
--   GET /djs/applet/breed/eartag/farrow-pending  返 { pendingFarrowCount, pendingPigletCount }
--
-- 角色绑定：与 D9X-MP-002 同范式 role_id NOT IN (1,101) 白名单，
-- 给所有 mp 真实角色（breed_worker / vet / breed_admin / boss / manager /
-- plant_admin / warehouse_admin / store_admin 等）。背景见
-- D09X/_open-issues.md #1（历史 LIKE %mp/applet/...% 范式在 ry-vue 库
-- role 命名 *_worker / *_admin / vet / boss 下不命中）。

-- ============================================================
-- 1. mp 耳标徽标权限菜单（visible='1' 隐藏；F 按钮型 perms-only；parent_id=0 root）
-- ============================================================
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7363, 'mp 仔猪耳标徽标', 0, 22, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:applet:breed:eartag', '#', 1, NOW(),
   'DJS-FIX-MP-W22-003 EartagAppletController GET farrow-pending');

-- ============================================================
-- 2. 挂权限给 mp 真实使用角色（admin role_id=1 / system_admin role_id=101 不绑）
-- ============================================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 7363 AS menu_id
FROM sys_role r
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0';
