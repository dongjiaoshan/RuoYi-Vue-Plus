-- BRD-DASH-001-be: 养殖看板 dashboard 菜单（menu_id 7400-7499）
--
-- menu_id 分段（CLAUDE.md §6 已固化给本 ticket）：
--   7400        养殖看板 父菜单（C 菜单 / parent_id=7000）
--   7410-7419   4 个查询权限 + 1 个聚合触发权限

-- ============================================================
-- 1. 父菜单 — 养殖看板（admin 端入口）
-- ============================================================
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7400, '养殖看板', 7000, 20, 'dashboard', 'djs-breed/dashboard/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:dashboard:view', 'chart', 1, NOW(), 'BRD-DASH-001 dashboard');

-- ============================================================
-- 2. 5 个按钮 / API 权限
-- ============================================================
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, menu_type, perms, status, visible, icon, create_by, create_time, remark)
VALUES
  (7410, '实时库存',     7400, 1, 'F', 'djs:breed:dashboard:inventory',  '0', '0', '#', 1, NOW(), 'GET /djs/breed/dashboard/inventory'),
  (7411, '月度对比',     7400, 2, 'F', 'djs:breed:dashboard:monthly',    '0', '0', '#', 1, NOW(), 'GET /djs/breed/dashboard/monthly-comparison'),
  (7412, '7 日活动统计', 7400, 3, 'F', 'djs:breed:dashboard:activity',   '0', '0', '#', 1, NOW(), 'GET /djs/breed/dashboard/activity-7d'),
  (7413, '年度指标',     7400, 4, 'F', 'djs:breed:dashboard:annual',     '0', '0', '#', 1, NOW(), 'GET /djs/breed/dashboard/annual'),
  (7414, '手动聚合',     7400, 5, 'F', 'djs:breed:dashboard:aggregate',  '0', '0', '#', 1, NOW(), 'POST /djs/breed/dashboard/trigger-aggregate（dev 调试）');
