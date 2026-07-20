-- ============================================================
-- FIX-DJS-PERM-MENU-008  养殖 mp tab 可见性导航权限移出 djs:applet:breed 命名空间
-- ============================================================
-- 问题（kevin-miniapp问题汇总 行2(b)）：admin 取消「疫苗药品/养殖管理」权限后，mp 底栏仍显示该 tab。
-- 根因：DJS-PERM-MENU-007 把三 tab 可见性导航权限定为 djs:applet:breed:nav:{home,med,dashboard}，
--   落在 djs:applet:breed:* 命名空间下；而「养殖·基础数据(11023)」授的正是宽通配 djs:applet:breed:*。
--   mp permMatch 双向匹配 → 用户只要有 djs:applet:breed:*（有养殖 tab 就有）即命中 :nav:med / :nav:dashboard
--   → 三 tab 恒显示，取消勾选压不住。djs:applet:breed:* 覆盖 6 个互不相交子族（barn/eartag/farrow/
--   growth/pen），无法收窄成单条不误伤的通配串，故把可见性 gate 移出该命名空间。
--
-- 解法：三导航子节点 perms 改到独立命名空间 djs:mptab:breed:{home,med,dashboard}——djs:applet:breed:*
--   不再命中它，解耦成立；超管 *:*:* 仍命中。功能端点通配串（11022/11023 等）一概不动，无 controller 改、无 403。
--   配套：miniapp/src/tabbar/config.ts breedTabbarList 三 tab permissions 同步改为 djs:mptab:breed:*。
-- 生效：PermissionService 颁 token 实时读 DB，mp 重新登录即生效；无需 flush redis。
-- ============================================================

UPDATE sys_menu SET perms = 'djs:mptab:breed:home'      WHERE menu_id = 11026;
UPDATE sys_menu SET perms = 'djs:mptab:breed:med'       WHERE menu_id = 11027;
UPDATE sys_menu SET perms = 'djs:mptab:breed:dashboard' WHERE menu_id = 11028;
