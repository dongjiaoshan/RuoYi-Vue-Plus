-- ============================================================
-- DJS-FIX-SYS-W22-004 menu seed 角色绑定范式统一（D10 hotfix）
--
-- 触发：D9X _open-issues.md #1 — D5-D9 期间 8 个 menu seed DDL 用了错范式：
--   - role_key IN ('pig_keeper','butcher') 等硬编码到不存在的 role_key
--   - role_key IN ('breed_admin','breed_worker') 仅 2 角色，导致 boss/manager/vet 等 9 个业务角色未绑
--   实际表现：admin 端引种 / 仔猪耳标 / 状态机 / 配种 / 死淘 / 生长记录等菜单
--   大量 biz 角色看不到（除 superadmin/system_admin 通配外）。
--
-- 影响范围（21 个 menu_id，来自 8 个历史 DDL）：
--   V202605242300 BRD-EVENT-001-003-admin-readonly-list-menu →（角色补绑 7110）
--   V202605260900 BRD-CORE-001-menu                          → 7200-7204（7203 已被 D07-HOTFIX 删）
--   V202605270901 BRD-EVENT-001-menu                          → 7100-7104
--   V202605270902 BRD-EVENT-003-menu                          → 7110-7112
--   V202605280800 D05-CLOSING-fixes                           →（角色补绑 7200-7202）
--   V202605281201 BRD-EVENT-002-be-menu                       → 已被 D06-HOTFIX 合并入 7145
--   V202605281300 BRD-EVENT-004-be-menu                       → 已被 D06-HOTFIX 合并入 7145
--   V202605281401 BRD-EVENT-005-be-menu                       → 7140-7144
-- 扩展 scope（同段同病）：
--   V202605282200 D06-HOTFIX-merge-event-menus                → 7145-7146（事件台账 admin 透视）
--
-- 修复范式（参 D9X MP-002 V202606081200 已验证白名单）：
--   list/query 类：role_id NOT IN (1, 101) AND del_flag='0' — 全 11 业务角色
--   write 类（新增/编辑/删除）：role_key IN 业务白名单（boss/manager/breed_admin/breed_worker/vet）
--   export 类：role_key IN 高敏白名单（boss/manager/breed_admin）
--
-- 历史 DDL 不动（Flyway hash 不可逆）；新 DDL 先清理脏绑定再重绑。
-- ============================================================

SET NAMES utf8mb4;

-- ============== 1. 清理脏绑定 ==============
-- 历史 DDL 即使硬编码不存在的 role_key，也可能绑上少量角色（boss/manager/breed_admin/breed_worker）
-- 全清后用白名单 + 业务角色限定重绑，确保最终态稳定可重放
DELETE FROM sys_role_menu WHERE menu_id IN (
  7100, 7101, 7102, 7103, 7104,
  7110, 7111, 7112,
  7140, 7141, 7142, 7143, 7144,
  7145, 7146,
  7200, 7201, 7202, 7204
) AND role_id NOT IN (1, 101);  -- 保留 superadmin/system_admin（perms 通配，无需 sys_role_menu 行）

-- ============== 2. list/query 类：全 11 业务角色 ==============
-- 二级目录 + 查询/详情/列表权限 — 所有 djs 业务角色可见（含养殖以外的 plant/warehouse/store
-- 角色），目录权限不细分；按钮级权限由下面 write/export 段限制。
-- role_id 1 = superadmin / 101 = system_admin，通过 perms 通配跳过。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 7100 AS menu_id UNION SELECT 7101 UNION SELECT 7102          -- 引种登记目录+查询+详情
    UNION SELECT 7110 UNION SELECT 7111                                  -- 仔猪耳标目录+查询
    UNION SELECT 7140 UNION SELECT 7141                                  -- 生长记录目录+查询
    UNION SELECT 7145 UNION SELECT 7146                                  -- 事件台账目录+查询
    UNION SELECT 7200 UNION SELECT 7201 UNION SELECT 7202                -- 猪只主表目录+查询+详情
) m
WHERE r.role_id NOT IN (1, 101) AND r.del_flag = '0';

-- ============== 3. write 类：boss/manager/breed_admin/breed_worker/vet（5 角色）==============
-- 引种新增 7103 / 批量贴标 7112 / 生长新增 7142 / 生长删除 7143
-- vet 兽医也可录事件（治疗/死亡/分娩协助）；plant/warehouse/store 角色无需写权限。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 7103 AS menu_id UNION SELECT 7112 UNION SELECT 7142 UNION SELECT 7143
) m
WHERE r.role_key IN ('boss', 'manager', 'breed_admin', 'breed_worker', 'vet')
  AND r.del_flag = '0';

-- ============== 4. export 类：boss/manager/breed_admin（3 角色）==============
-- 引种导出 7104 / 生长导出 7144 / 猪只导出 7204 — 高敏（含工人/兽医不下放）。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 7104 AS menu_id UNION SELECT 7144 UNION SELECT 7204
) m
WHERE r.role_key IN ('boss', 'manager', 'breed_admin')
  AND r.del_flag = '0';

-- ============== 5. 自检（reports 单独跑确认）==============
-- SELECT m.menu_id, m.menu_name, m.menu_type,
--   (SELECT COUNT(*) FROM sys_role_menu rm WHERE rm.menu_id = m.menu_id AND rm.role_id NOT IN (1,101)) AS biz_bind
-- FROM sys_menu m
-- WHERE m.menu_id IN (7100,7101,7102,7103,7104, 7110,7111,7112, 7140,7141,7142,7143,7144,
--                     7145,7146, 7200,7201,7202,7204)
-- ORDER BY m.menu_id;
-- 期望：
--   list/query (12 个 menu): biz_bind = 11
--   write (4 个: 7103/7112/7142/7143): biz_bind = 5
--   export (3 个: 7104/7144/7204): biz_bind = 3
