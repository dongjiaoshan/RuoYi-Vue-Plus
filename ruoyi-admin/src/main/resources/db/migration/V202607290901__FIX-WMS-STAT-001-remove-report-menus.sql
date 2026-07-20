-- ============================================================
-- FIX-WMS-STAT-001  删除 admin 3 个统计报表菜单（仓库日报表/作物日报表/仓库月报表）
-- ============================================================
-- 客户（kevin-miniapp问题汇总 行3）：这三个报表 admin 端不需要——同一份统计已挪到
-- mp 总经理「仓库管理」面板展示（消费 t_warehouse_indicator/cropp/monthly_record 同表）。
-- 删走新迁移、不改旧 seed（CLAUDE.md §6#14）。三菜单 path 非空，删除不触发空 path 崩 router。
--
-- 仅删菜单 + 授权行：后端 WarehouseStatController / 3 张统计表 / WarehouseStatJob 夜跑 / plus-ui
-- warehouseStat 视图 保留不动（mp 仓库面板复用同数据层，删菜单后 admin 入口不可达即可）。
-- sys_menu / sys_role_menu 为 ruoyi 框架表，无 tenant_id。
-- 生效：菜单/路由由 /getRouters 实时下发，重新登录 admin 即不再出现这三项；无需 flush redis。
-- ============================================================

DELETE FROM sys_role_menu WHERE menu_id IN (9131, 9132, 9133);
DELETE FROM sys_menu      WHERE menu_id IN (9131, 9132, 9133);
