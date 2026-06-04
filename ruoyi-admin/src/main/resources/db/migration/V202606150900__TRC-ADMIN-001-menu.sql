-- ============================================================
-- TRC-ADMIN-001  追溯码管理  菜单 seed（10500-10506 段）
-- ============================================================
-- TRC 域在 SYS-AUTH-001 未预占一级父菜单 → 本 ticket 自治 seed 追溯一级父菜单 10500
--   （ADR-0006 "M1 一次性 seed" 例外，下游 TRC ticket V3 顾客扫码直接挂 10500，不再重 seed 父）
-- menu_id 段：10500-10549（门店+追溯+DSH 域 10000-10999，本 ticket 占追溯 TRC 段）
-- 权限串：djs:warehouse:trace:{list,query,export,print}（与 be @SaCheckPermission 完全一致）
--   追溯后端归 warehouse 模块，故权限走 djs:warehouse:trace:* 全命名空间（不用 djs:trace:* 短前缀）
-- 追溯码管理 admin only，无 mp 子菜单；追溯链不可篡改，无新增/删除/编辑按钮
-- role_menu 白名单：role_id NOT IN (1, 101) AND del_flag='0'（追溯是跨域查询型功能，绑给全部业务角色）
--   + 超管 role_id=1 全绑
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. sys_menu seed 10500-10505（父 M + C + 4 个 F；10506 预留不 seed）
-- ------------------------------------------------------------
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 追溯一级父菜单（M，parent_id=0）
    (10500, '追溯', 0, 7, 'trace-mgmt', NULL, '',
     1, 0, 'M', '0', '0', NULL, 'guide', 1, NOW(), 'TRC-ADMIN-001 追溯一级父菜单（ADR-0006 M1 例外）'),
    -- 追溯码管理（C，单页用 code_type tab 切 猪肉/果蔬/礼盒 3 套）
    (10501, '追溯码管理', 10500, 1, 'trace', 'warehouse/trace/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:trace:list', 'barcode', 1, NOW(), 'TRC-ADMIN-001 admin 追溯码 3 套'),
    (10502, '列表查询', 10501, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:trace:list', '#', 1, NOW(), 'TRC-ADMIN-001'),
    (10503, '详情查询', 10501, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:trace:query', '#', 1, NOW(), 'TRC-ADMIN-001 详情 + 事件时间轴'),
    (10504, '导出', 10501, 3, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:trace:export', '#', 1, NOW(), 'TRC-ADMIN-001'),
    (10505, '批量打印', 10501, 4, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:trace:print', '#', 1, NOW(), 'TRC-ADMIN-001 jsPDF 批量出码');

-- ------------------------------------------------------------
-- 2. role_menu 白名单绑定（role_id NOT IN (1, 101) — superadmin/system_admin perms 通配）
--    追溯是跨域查询型功能（boss/manager/各域 admin 都可能查追溯）→ 绑全部业务角色
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id BETWEEN 10500 AND 10506;

-- 超级管理员角色 1 全绑
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id BETWEEN 10500 AND 10506;
