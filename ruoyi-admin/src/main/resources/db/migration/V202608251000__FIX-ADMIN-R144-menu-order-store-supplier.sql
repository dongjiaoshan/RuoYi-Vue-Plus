-- admin row144：「门店管理」「供应商管理」在系统管理目录里移到「岗位管理」下面（客户圈定）。
-- 系统管理(menu_id=1) 可见子菜单最终顺序：
--   用户管理1 · 角色管理2 · 菜单管理3 · 部门管理4 · 岗位管理5 ·
--   门店管理6 · 供应商管理7 · 字典管理8 · 参数设置9 · 日志管理10 · 文件管理11 · 定时任务重跑12

UPDATE sys_menu SET order_num = 6  WHERE menu_id = 5002;  -- 门店管理
UPDATE sys_menu SET order_num = 7  WHERE menu_id = 5003;  -- 供应商管理
UPDATE sys_menu SET order_num = 8  WHERE menu_id = 105;   -- 字典管理
UPDATE sys_menu SET order_num = 9  WHERE menu_id = 106;   -- 参数设置
UPDATE sys_menu SET order_num = 10 WHERE menu_id = 108;   -- 日志管理
UPDATE sys_menu SET order_num = 11 WHERE menu_id = 118;   -- 文件管理
UPDATE sys_menu SET order_num = 12 WHERE menu_id = 5600;  -- 定时任务重跑
