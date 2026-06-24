-- ============================================================
-- STR-USER-REL-001 (STORE-PERM-001)  门店人员选择 按钮菜单
-- ============================================================
-- menu_id 5026（F，挂门店管理 5002，紧随设置店长 5025）：门店列表操作列「门店人员选择」。
-- perms djs:common:store:bindUser 守 4 端点（GET/PUT/DELETE /{storeId}/users）。
-- role_menu 白名单范式：业务角色 role_id NOT IN(1,101) + 超管 role_id=1 全绑。
-- 新 menu_id 不被旧 BETWEEN select 覆盖，必须显式补 sys_role_menu（否则真机看不到按钮）。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. sys_menu seed
-- ------------------------------------------------------------
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (5026, '门店人员选择', 5002, 6, '', '', '',
     1, 0, 'F', '0', '0', 'djs:common:store:bindUser', '#', 1, NOW(), 'STR-USER-REL-001 门店人员绑定');

-- ------------------------------------------------------------
-- 2. role_menu 白名单绑定（业务角色 + 超管）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id IN (5026);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id FROM sys_menu m WHERE m.menu_id IN (5026);
