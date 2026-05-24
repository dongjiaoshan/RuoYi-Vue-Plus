-- ============================================================
-- BRD-EVENT-001/003 admin 端补只读列表页菜单
--
-- testing-human B 阶段：admin 左侧「引种登记」/「仔猪耳标」点击空白页。
-- doc/02 scope = be+fe-app（只 mp 录），admin 端补只读历史查询页（不录入）。
--
-- 1) menu 7110「仔猪耳标」从 M（目录）改 C（页面）+ 加 path/component/perms
-- 2) breed_admin / breed_worker 加 7110 菜单访问
-- 3) intro/eartag :list 权限赋给 breed_admin / breed_worker（boss/manager 已有）
-- ============================================================

UPDATE sys_menu
SET menu_type = 'C',
    path = 'eartag',
    component = 'djs-breed/event/eartag/index',
    perms = 'djs:breed:event:eartag:list'
WHERE menu_id = 7110;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 7110 FROM sys_role r
WHERE r.role_key IN ('breed_admin', 'breed_worker');
