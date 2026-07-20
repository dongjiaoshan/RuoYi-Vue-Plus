-- K211 门店看板「去掉」（R5 a）：隐藏门店看板菜单（STR-DASH-001 / menu_id 10400），
-- 不删菜单/路由/文件/表，留 V2。visible='1' = 隐藏（不进动态路由 + 侧边栏不显）。
-- 可逆：Kevin 若决定恢复，UPDATE 回 visible='0' 即可。
UPDATE sys_menu SET visible = '1' WHERE menu_id = 10400;
