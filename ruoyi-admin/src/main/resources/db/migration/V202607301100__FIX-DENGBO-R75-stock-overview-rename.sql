-- 库存总览菜单改名为「库存日汇总」（仓库域 9123，挂库存管理 9302）
UPDATE sys_menu SET menu_name = '库存日汇总' WHERE menu_id = 9123 AND menu_name = '库存总览';
