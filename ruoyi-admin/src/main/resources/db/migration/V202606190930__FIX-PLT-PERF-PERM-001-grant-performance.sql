-- ============================================================
-- FIX-PLT-PERF-PERM-001  班组绩效菜单补授（种植授权缺口收口）
-- ============================================================
-- 缘由：FIX-PLT-DASH-PERM-001 排查种植域授权时发现，种植 admin 子菜单
--       （8010 地块 / 8020 作物 / 8070 计划 等）早已广授业务角色 102-112，
--       唯独 8200「班组绩效」+ F 子按钮（8201/8202/8203）只授超管 role_id=1
--       → 任何业务角色都看不到班组绩效（PLT-PERF-001 D12 落地时漏授）。
-- 决策（Kevin 2026-06-08）：按「与看板一致」补授给种植管理类角色
--       boss(102) / manager(103) / plant_admin(105)（绩效=管理职能，不授工人/他域）。
-- 守 §6：仅补 sys_role_menu 关联，不动 ruoyi 自带表结构，不新增 menu_id。
-- ============================================================

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (SELECT 102 AS role_id UNION ALL SELECT 103 UNION ALL SELECT 105) r
CROSS JOIN (SELECT menu_id FROM sys_menu WHERE menu_id BETWEEN 8200 AND 8203) m;
