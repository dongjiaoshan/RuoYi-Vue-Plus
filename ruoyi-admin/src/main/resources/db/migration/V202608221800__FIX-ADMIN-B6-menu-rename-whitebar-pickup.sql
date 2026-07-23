-- 菜单「分割白条领用」改名为「白条出库领用」（仅改 menu_name，路由/权限/父级不动）
UPDATE sys_menu SET menu_name = '白条出库领用' WHERE menu_id = 9238;
