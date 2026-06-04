-- ============================================================
-- WMS-DEMAND-002 mp 调度菜单 seed（9270-9275 共 6 行 menu）
--
-- ADR-0006 菜单分治：仓库域 9000-10999；mp 需求调度占 9270-9279 段
--   父菜单 9000 仓库 已由 D7 WMS-MD-001 V202605290910 seed
--   admin 端需求管理 9040-9054（WMS-DEMAND-001）/ 发货月台 9200-9249（WMS-SHIP-001）已占
--
-- 结构：
--   9270 需求调度 M2 父菜单（mp 入口 perms 容器，admin 端不挂 vue；
--         mp 调度员通过 pages/warehouse/dispatch/home 触达，不挂 admin 路由）
--     9271-9275 mp 端 5 子页 perms：调度首页 / 白条需求 / 蔬菜需求 / 猪只选择 / 统计
--
-- 角色绑定（D10 DJS-FIX-SYS-W22-004 白名单范式）：
--   全部为 mp 调度员可见入口 → list/query 类：role_id NOT IN (1, 101) AND del_flag='0'
--   （super admin 1 走 *:*:* 不需显式绑；101 为预留 demo 角色，不下放业务）
--
-- 鉴权说明：be 端 mp endpoint V1 走 @SaCheckLogin（不查 perm，ADR-0003 mock auth）；
--   本 menu seed 仅为 V1.5 升 @SaCheckPermission("djs:applet:warehouse:demand:dispatch:*")
--   预埋 perms + 控制 mp 板块入口可见性。
-- ============================================================

SET NAMES utf8mb4;

-- ============== 1. menu seed ==============
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
    (9270, '需求调度', 9000, 27, 'demand-dispatch', NULL, '', 1, 0, 'M', '0', '0', '',                                              'rate',     1, NOW(), 'WMS-DEMAND-002 mp 调度员入口容器'),
    (9271, '调度首页 mp', 9270, 1, '', '', '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:demand:dispatch:home',     '#',        1, NOW(), 'WMS-DEMAND-002 mp 调度首页 KPI'),
    (9272, '白条需求 mp', 9270, 2, '', '', '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:demand:dispatch:whiteBar', '#',        1, NOW(), 'WMS-DEMAND-002 mp 白条需求列表 + 选猪 + 确认'),
    (9273, '蔬菜需求 mp', 9270, 3, '', '', '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:demand:dispatch:vegetable','#',        1, NOW(), 'WMS-DEMAND-002 mp 蔬菜需求列表 + 确认'),
    (9274, '猪只选择 mp', 9270, 4, '', '', '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:demand:dispatch:pigSelect','#',        1, NOW(), 'WMS-DEMAND-002 mp 出栏可用选猪 + 分配'),
    (9275, '调度统计 mp', 9270, 5, '', '', '', 1, 0, 'F', '0', '0', 'djs:applet:warehouse:demand:dispatch:stats',    '#',        1, NOW(), 'WMS-DEMAND-002 mp 5 dashboard 统计');

-- ============== 2. role_menu 白名单绑定 ==============
-- 调度入口 + 5 子页 perms 全部为 mp 调度员可见 → 全 11 业务角色（排除 super admin 1 / demo 101）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 9270 AS menu_id UNION SELECT 9271 UNION SELECT 9272
    UNION SELECT 9273 UNION SELECT 9274 UNION SELECT 9275
) m
WHERE r.role_id NOT IN (1, 101) AND r.del_flag = '0';
