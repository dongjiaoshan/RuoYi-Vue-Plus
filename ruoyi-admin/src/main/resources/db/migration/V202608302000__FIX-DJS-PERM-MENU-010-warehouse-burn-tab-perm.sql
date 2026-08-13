-- ============================================================
-- FIX-DJS-PERM-MENU-010  仓库 mp「燎毛间」tab 可见性接上角色树
-- ============================================================
-- 问题（甲方 2026-08-13）：角色树里没给「燎毛间」权限，进仓库小程序底栏仍显示「燎毛间」。
--
-- 根因两层，缺一不可：
--   1) sys_menu 11068「燎毛间」perms = ''（DJS-PERM-MENU-005 建时只当分组节点用，
--      注释写明「首页聚合，tabbar 该 tab 不 gate，节点仅整理授权树」）。
--      SysMenuMapper.selectMenuPermsByUserId 会 filter 掉空串 → 勾/取消勾都不产生任何权限串。
--   2) miniapp tabbar 该 tab 没写 permissions → tabbar/store.ts 走「无 gate 恒显」分支。
--
-- 解法：给 11068 配独立导航命名空间权限串 djs:mptab:warehouse:burn。
--   为什么不复用端点通配：燎毛列表端点 AppletPigBurnRecordController 无 @SaCheckPermission，
--   没有干净的 applet burn perm 可用；而把 tab-perm 放进 djs:applet:warehouse:* 下会被
--   permMatch 双向通配互相命中，复现 FIX-DJS-PERM-MENU-008 的「取消勾选压不住」。
--   与养殖 djs:mptab:breed:* / 门店 djs:mptab:store:demand 同一套路。
--   11068 是 menu_type='M'，selectMenuPermsByUserId 不按 menu_type 过滤，与 11051 同理可行。
--
-- 授权范围：已持有 11068 的角色（系统管理员/老板/管理人员/仓库管理员/燎毛工）自动获得该串继续可见；
--   未勾选的角色（蔬菜处理工/盘点员/司机）自然失去该 tab，正是甲方要的效果。
--   仓库工人(110) 单独补勾 —— Kevin 2026-08-13 定：权限只由勾选决定、与角色名无关，
--   该角色在役 14 人当前实际能进燎毛间，不能因为本次修复而掉权限。
--   只补 11068 本身、不补子节点 11069/11070：与库里既有的半选父节点形态一致
--   （110 已持 11042 但只持 3/4 子节点），最小授权。
--   落地页由 miniapp resolveLandingPath 取「首个可见 tab」，不会落到无权限页。
--
-- 配套：miniapp/src/tabbar/config.ts warehouseTabbarList 燎毛间 tab
--   permissions = ['djs:mptab:warehouse:burn']。
-- 生效：PermissionService 颁 token 实时读 DB，mp 重新登录即生效；无需 flush redis。
-- ============================================================

UPDATE sys_menu SET perms = 'djs:mptab:warehouse:burn' WHERE menu_id = 11068;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (110, 11068);
