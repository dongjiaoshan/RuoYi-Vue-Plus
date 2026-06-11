-- K216 会员档案「去掉」（R5 a）：隐藏会员档案菜单及其子权限（STR-MEMBER-001 / menu_id 10320-10327），
-- 不删菜单/路由/文件/表，留 V2。visible='1' = 隐藏（C 菜单不进动态路由 + 侧边栏不显；F 子权限隐藏不影响）。
-- 可逆：Kevin 若决定恢复，UPDATE 回 visible='0' 即可。
UPDATE sys_menu SET visible = '1' WHERE menu_id BETWEEN 10320 AND 10327;
