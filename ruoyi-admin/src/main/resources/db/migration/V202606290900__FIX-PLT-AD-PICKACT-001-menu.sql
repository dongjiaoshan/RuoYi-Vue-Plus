-- ============================================================
-- FIX-PLT-AD-PICKACT-001 采摘活动改回只读每日采摘聚合报表
--
-- 采摘活动从「游客采摘 CRUD + 状态机」回滚为「只读每日采摘聚合报表」（仅查询 + 导出）。
-- 本迁移删除不再使用的：
--   1. 字典 djs_pick_activity_status（dict_type + 3 条 dict_data）—— 跑后由 _post-init.sh flush redis
--   2. 采摘活动 CRUD 的 F 权限菜单 8085/8086/8087（新增/编辑/删除）+ 对应 sys_role_menu
--
-- 保留：8084 采摘活动主菜单（仍是只读报表入口）+ t_plant_pick_activity 表（历史数据，端点废弃不 DROP）。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 删字典 djs_pick_activity_status
-- ------------------------------------------------------------
DELETE FROM sys_dict_data WHERE dict_type = 'djs_pick_activity_status';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_pick_activity_status';

-- ------------------------------------------------------------
-- 2. 删采摘活动 CRUD 的 F 权限菜单（8085 新增 / 8086 编辑 / 8087 删除）
--    三处齐：sys_role_menu → sys_menu
-- ------------------------------------------------------------
DELETE FROM sys_role_menu WHERE menu_id IN (8085, 8086, 8087);
DELETE FROM sys_menu      WHERE menu_id IN (8085, 8086, 8087);
