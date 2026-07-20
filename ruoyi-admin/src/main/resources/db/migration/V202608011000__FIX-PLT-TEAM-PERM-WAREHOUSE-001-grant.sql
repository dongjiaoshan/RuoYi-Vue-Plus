-- FIX-PLT-TEAM-PERM-WAREHOUSE-001 — 补授仓库角色「班组下拉」权限（测试 row7：仓库角色进采摘活动记录页
--   提示「没有访问权限，请联系管理员授权」）。
--
-- 根因：采摘活动记录页（pages/plant/work/harvest）载入时拉「班组下拉」→ GET /djs/applet/plant/team/list，
--   该接口 @SaCheckPermission("djs:applet:plant:team:list")（menu_id 11034，DJS-PERM-MENU-004 建），
--   当时只授给种植相关 6 角色（101 system_admin / 102 boss / 103 manager / 105 plant_admin /
--   108 breed_worker / 111 plant_worker），未含仓库角色。真微信登录走真权限（非通配），仓库工人 / 仓库
--   管理员进该页 → Sa-Token 抛 NotPermissionException → 前端「没有访问权限」。页内其余接口
--   （cropTasks / cropPlots / activity records / adjustPlotPickStatus）均 @SaCheckLogin 免权限，唯班组下拉需授。
--
-- 修法：把 11034（djs:applet:plant:team:list，只读班组 picker）授给仓库工人(110) + 仓库管理员(106)。
--   跨域只读授权（仓库人员在采摘活动记录页按班组筛选），安全、幂等（INSERT IGNORE）。不新建菜单、不改 11034 本身。
-- 授权后【真微信用户需重新登录】（token 的 menuPermission 是登录态快照）；admin 在跑则跑
--   script/sql/djs/_post-init.sh flush redis。纯 SQL，Flyway 自动应用，无需 mvn install。

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 11034
FROM sys_role r
WHERE r.role_key IN ('warehouse_worker', 'warehouse_admin')
  AND r.del_flag = '0';
