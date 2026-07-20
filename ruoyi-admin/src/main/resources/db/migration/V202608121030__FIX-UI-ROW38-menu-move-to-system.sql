-- UI问题 row38：把「通用主数据」下的 3 个管理菜单挪到「系统管理」目录下（客户圈定）。
--   门店管理(5002) / 供应商管理(5003) / 定时任务重跑(5600) → parent 系统管理(menu_id=1)
-- 排在系统管理现有子菜单（order_num 1-10）之后。
-- 注：移走后「通用主数据(5000)」仅剩 F 型权限（查询/切换农场 · OSS 上传凭证），侧边栏不再渲染该目录。

UPDATE sys_menu SET parent_id = 1, order_num = 11 WHERE menu_id = 5002;  -- 门店管理
UPDATE sys_menu SET parent_id = 1, order_num = 12 WHERE menu_id = 5003;  -- 供应商管理
UPDATE sys_menu SET parent_id = 1, order_num = 13 WHERE menu_id = 5600;  -- 定时任务重跑
