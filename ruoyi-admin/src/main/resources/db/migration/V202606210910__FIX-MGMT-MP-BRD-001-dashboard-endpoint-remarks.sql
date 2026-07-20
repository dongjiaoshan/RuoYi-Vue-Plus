-- FIX-MGMT-MP-BRD-001：养殖管理看板新增 5 个 dashboard 聚合端点。
-- 这 5 个端点复用 BRD-DASH-001 已有的 4 个 F 权限（不新建 perm/menu，boss/dashboard 角色已持有）：
--   GET /djs/breed/dashboard/daily-overview              → djs:breed:dashboard:activity   (7412)
--   GET /djs/breed/dashboard/breeding-annual             → djs:breed:dashboard:annual     (7413)
--   GET /djs/breed/dashboard/fattening-age-distribution  → djs:breed:dashboard:inventory  (7410)
--   GET /djs/breed/dashboard/fattening-trend             → djs:breed:dashboard:inventory  (7410)
--   GET /djs/breed/dashboard/monthly-production-stats     → djs:breed:dashboard:monthly    (7411)
-- by-month 端点指标由 11 → 13（生长记录数 + 阉割猪只数），契约不变仍走 7412。
-- 本迁移仅更新对应 F 菜单 remark 以反映当前端点覆盖（无表结构 / 无新权限）。

UPDATE sys_menu SET remark = 'GET /djs/breed/dashboard/inventory + /fattening-age-distribution + /fattening-trend'
 WHERE menu_id = 7410 AND perms = 'djs:breed:dashboard:inventory';

UPDATE sys_menu SET remark = 'GET /djs/breed/dashboard/monthly-comparison + /monthly-production-stats'
 WHERE menu_id = 7411 AND perms = 'djs:breed:dashboard:monthly';

UPDATE sys_menu SET remark = 'GET /djs/breed/dashboard/activity-7d + /activity/by-month(13行) + /daily-overview(16格)'
 WHERE menu_id = 7412 AND perms = 'djs:breed:dashboard:activity';

UPDATE sys_menu SET remark = 'GET /djs/breed/dashboard/annual + /breeding-annual'
 WHERE menu_id = 7413 AND perms = 'djs:breed:dashboard:annual';
