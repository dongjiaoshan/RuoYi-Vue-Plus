-- DJS-FIX A5：猪只详情页 404 —— 补 detail 隐藏路由菜单
-- 根因：admin 猪只列表「全部 / sow / fattening」点详情 router.push('/djs-breed/pig/detail/:id')，
--       但 sys_menu 下 7200 猪只主表只有 F 按钮子菜单，无 detail 的隐藏 C 路由菜单 → 前端动态路由未注册 → 404。
-- 修法：补一条隐藏 C 菜单（path 带 :id 路径参数，与 detail.vue route.params.id + 各页 push 路径一致），
--       不改任何前端代码。范式照 8078 计划详情（djs-plant/plan/detail），路由 = 父(djs-breed) + pig/detail/:id。
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param,
   is_frame, is_cache, menu_type, visible, status, perms,
   icon, create_by, create_time, remark)
VALUES
  (7205, '猪只详情页', 7000, 15, 'pig/detail/:id', 'djs-breed/pig/detail', '',
   1, 0, 'C', '1', '0', 'djs:breed:pig:query',
   '#', 1, NOW(), 'DJS-FIX A5 猪只详情隐藏路由（admin 只读，path 参数 :id；visible=1 不进侧边栏）');

-- 绑给和「猪只主表」(7200) 相同的角色，否则该路由不会为这些角色生成（隐藏路由仍需在角色菜单集内）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 7205 FROM sys_role_menu WHERE menu_id = 7200;
