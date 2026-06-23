-- FIX：mp 权限树容器（11000-11050）补 path。
--
-- 问题：DJS-PERM-MENU-001 建这 6 个 menu_type='M' 容器时 path 留空。ruoyi /getRouters 把所有
-- M/C 菜单下发为路由（visible='1' 只隐藏侧边栏、不排除路由），空 path 到前端 vue-router
-- addRoute 判 `path[0] !== '/'` 时崩 → 动态路由全挂 → admin 整页白屏。
-- 修：补非空 path（仍 visible='1' 隐藏、component 留 NULL 由 ruoyi 算 Layout/ParentView）。

UPDATE sys_menu SET path = 'mp-perm'       WHERE menu_id = 11000 AND (path IS NULL OR TRIM(path) = '');
UPDATE sys_menu SET path = 'mp-common'     WHERE menu_id = 11010 AND (path IS NULL OR TRIM(path) = '');
UPDATE sys_menu SET path = 'mp-breed'      WHERE menu_id = 11020 AND (path IS NULL OR TRIM(path) = '');
UPDATE sys_menu SET path = 'mp-plant'      WHERE menu_id = 11030 AND (path IS NULL OR TRIM(path) = '');
UPDATE sys_menu SET path = 'mp-warehouse'  WHERE menu_id = 11040 AND (path IS NULL OR TRIM(path) = '');
UPDATE sys_menu SET path = 'mp-store'      WHERE menu_id = 11050 AND (path IS NULL OR TRIM(path) = '');
