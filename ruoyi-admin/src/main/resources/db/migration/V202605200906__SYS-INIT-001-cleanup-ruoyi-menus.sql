-- ============================================================
-- SYS-INIT-001 — 步骤 4：清理 ruoyi 自带不用的菜单
-- 生成时间: 2026-05-20
-- 原则：隐藏（visible='1'）而非物理删，便于回滚；demo 物理删（不会再用）
-- ruoyi 约定：visible '0'=显示 '1'=隐藏
-- ============================================================

SET NAMES utf8mb4;

-- 服务器监控（不用）— 服务监控 / 缓存监控 / 在线用户
UPDATE sys_menu SET visible='1' WHERE menu_name IN ('服务监控', '缓存监控', '在线用户');

-- 系统工具 — 表单构建 / 通知公告 不用
UPDATE sys_menu SET visible='1' WHERE menu_name IN ('表单构建', '通知公告');

-- demo 模块菜单物理删（确认不再用）
DELETE FROM sys_menu WHERE perms LIKE 'demo:%';
