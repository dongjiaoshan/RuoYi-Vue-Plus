-- ============================================================
-- DJS-HOTFIX-D11-001 mp 端 4 个错范式 menu seed 角色绑定重 seed（D11 早第一件事）
--
-- 触发：D10 closing _open-issues #1（Kevin "全 a"）— D5-D9 共 12 个历史 menu seed DDL
--   用错范式 role_key LIKE '%mp%'/'%applet%'/'%staff%'/'%employee%'（库里这些 role_key
--   不存在，LIKE 永不命中），导致 mp 端 picker / 物资领用端点权限 biz_bind=0，
--   除 superadmin(1)/system_admin(101) perms 通配外，mp 用户全部 403。
--   D10 DJS-FIX-SYS-W22-004 已修养殖域 8 个 DDL；剩 4 个 mp 端 DDL 本 hotfix 一次性收口。
--
-- 4 个源 DDL 的错范式 sys_role_menu 绑段（grep 枚举出的真实 menu_id 全集）：
--   V202605300900 BRD-LIST-001 §5              → 7360                 （mp 猪只 picker 搜索）
--   V202606071110 MP-UX-002    §2              → 5350, 5351, 5352     （门店 / 库位 / 产品 picker）
--   V202606071540 D9-closing   §2              → 5353, 5354, 5355     （分娩 / 栋舍 / 栏位 picker）
--   V202606071300 WMS-MAT-001  §4.3            → 9106, 9107, 9108     （物资领用 / 退回 / 损耗）
--   全集（10 个）：5350,5351,5352,5353,5354,5355,7360,9106,9107,9108
--
-- 修复范式（参 D10 V202606091000 DJS-FIX-SYS-W22-004 §1-2 已验证白名单）：
--   这 10 个 menu 全是 mp 工人端点权限（picker 搜索 + 物资领用/退回/损耗），无 export/write
--   高敏区分需求 → 统一 list/query 类白名单：role_id NOT IN (1,101) AND del_flag='0'，
--   11 个业务角色全绑。
--
-- 历史 DDL 不动（Flyway hash 不可逆）；本 DDL 先清脏绑定再用白名单重绑，单文件原子可重放。
-- 不动 sys_menu 表（这 10 个 menu_id 已在 4 个历史 DDL seed 进去，本 hotfix 只重 seed
-- sys_role_menu 绑定关系）；不删 1/101 角色绑（perms 通配，无需 sys_role_menu 行）。
-- ============================================================

SET NAMES utf8mb4;

-- ============== 1. 清脏绑定（保留 superadmin(1)/system_admin(101)）==============
DELETE FROM sys_role_menu
WHERE menu_id IN (5350, 5351, 5352, 5353, 5354, 5355, 7360, 9106, 9107, 9108)
  AND role_id NOT IN (1, 101);

-- ============== 2. 白名单重绑：全 11 业务角色 ==============
-- role_id 1 = superadmin / 101 = system_admin，perms 通配跳过；
-- 其余 11 个业务角色（boss/manager/breed_admin/breed_worker/vet/plant_admin/plant_worker/
-- warehouse_admin/warehouse_worker/store_admin/store_clerk）全绑，让 mp 用户登录后能调
-- picker 搜索端点 + 物资领用/退回/损耗端点。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 5350 AS menu_id UNION SELECT 5351 UNION SELECT 5352
    UNION SELECT 5353 UNION SELECT 5354 UNION SELECT 5355
    UNION SELECT 7360
    UNION SELECT 9106 UNION SELECT 9107 UNION SELECT 9108
) m
WHERE r.role_id NOT IN (1, 101) AND r.del_flag = '0';

-- ============== 3. 自检（reports 单独跑确认）==============
-- SELECT m.menu_id, m.menu_name,
--   (SELECT COUNT(*) FROM sys_role_menu rm WHERE rm.menu_id = m.menu_id AND rm.role_id NOT IN (1,101)) AS biz_bind
-- FROM sys_menu m
-- WHERE m.menu_id IN (5350,5351,5352,5353,5354,5355,7360,9106,9107,9108)
-- ORDER BY m.menu_id;
-- 期望：每个 menu_id biz_bind = 11（= SELECT COUNT(*) FROM sys_role WHERE role_id NOT IN (1,101) AND del_flag='0'）
