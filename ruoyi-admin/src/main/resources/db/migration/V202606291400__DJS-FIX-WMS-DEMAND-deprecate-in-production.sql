-- ============================================================
-- DJS-FIX-WMS-DEMAND-001  需求状态机去「开始排产」：废弃 IN_PRODUCTION 态（D-FIX-24 决策 #7a）
-- ============================================================
-- 需求确认即用户侧最终态：去掉「开始排产」动作与 CONFIRMED→IN_PRODUCTION 转移，CONFIRMED 后
-- 由发货链路（CROSS-FLOW-003 listener）直接推进 PARTIAL_SHIPPED / COMPLETED。
-- 存量卡在 IN_PRODUCTION 的需求单回迁到 CONFIRMED，使其能走新的「确认后直接发货」转移；
-- 已部分发货（PARTIAL_SHIPPED）/ 已完成 / 已取消的数据不动。
-- ============================================================
SET NAMES utf8mb4;

UPDATE t_warehouse_demand_manage
SET demand_status = 'CONFIRMED'
WHERE demand_status = 'IN_PRODUCTION'
  AND del_flag = '0';

-- 删除已废弃的「开始排产」按钮权限菜单（menu_id 9050，djs:warehouse:demand:start_production）：
-- 端点与状态机转移均已移除，该按钮权限成孤儿，删之。同步清 role 绑定关系。
DELETE FROM sys_role_menu WHERE menu_id = 9050;
DELETE FROM sys_menu WHERE menu_id = 9050 AND perms = 'djs:warehouse:demand:start_production';

