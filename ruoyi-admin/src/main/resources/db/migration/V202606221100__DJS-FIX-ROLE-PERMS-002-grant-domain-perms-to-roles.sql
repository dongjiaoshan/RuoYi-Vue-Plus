-- DJS-FIX-ROLE-PERMS-002：按「角色 ↔ 板块域」把全部 djs 业务权限补授给业务角色
--
-- 背景
--   接 DJS-FIX-APPLET-PERMS-001（只补了 djs:applet:*）。真机暴露：mp 端点不止用 djs:applet:*，
--   还大量复用 admin 命名空间权限——例如 mp 养殖板块首页调：
--     /djs/breed/dashboard/inventory        → djs:breed:dashboard:inventory
--     /djs/breed/dashboard/daily-overview   → djs:breed:dashboard:activity
--     /djs/breed/event/breeding/list        → djs:breed:event:breeding:list
--   这些 djs:breed:* / djs:plant:* / djs:warehouse:* / djs:store:* 权限的角色分配历史上
--   不完整（Pattern-A 模糊匹配 / 只挂了部分角色），被 mock 账号 *:*:* 长期掩盖；真微信登录
--   用真实权限后，mp 各板块复用 admin 权限的接口对真用户 403（"没有访问权限"）。
--
-- 修复（按 UserBoardController 角色→板块映射）
--   - 管理角色 boss / manager / system_admin：监管全域 → 授全部 djs:%（含 manage 板块聚合）。
--   - 各域工人/管理员：授本域 djs:<域>:% + 对应 applet 权限。
--   - 跨域通用（common 主数据 / oss 直传）：全员。
--   INSERT IGNORE 与既有正确 seed + DJS-FIX-APPLET-PERMS-001 幂等共存。
--
-- 注：sys_role 有 del_flag；sys_menu 无逻辑删除，不过滤。superadmin(role_id=1) 已 *:*:* 通配，跳过。

-- 1. 管理角色（boss / manager / system_admin）→ 全部 djs 权限
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r CROSS JOIN sys_menu m
WHERE m.perms LIKE 'djs:%'
  AND r.del_flag = '0'
  AND r.role_key IN ('boss', 'manager', 'system_admin');

-- 2. 养殖域 → breed_admin / breed_worker / vet
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r CROSS JOIN sys_menu m
WHERE (m.perms LIKE 'djs:breed:%' OR m.perms LIKE 'djs:applet:pig:%' OR m.perms LIKE 'djs:applet:breed:%')
  AND r.del_flag = '0'
  AND r.role_key IN ('breed_admin', 'breed_worker', 'vet');

-- 3. 种植域 → plant_admin / plant_worker
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r CROSS JOIN sys_menu m
WHERE (m.perms LIKE 'djs:plant:%' OR m.perms LIKE 'djs:applet:plant:%')
  AND r.del_flag = '0'
  AND r.role_key IN ('plant_admin', 'plant_worker');

-- 4. 仓库域 → warehouse_admin / warehouse_worker
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r CROSS JOIN sys_menu m
WHERE (m.perms LIKE 'djs:warehouse:%' OR m.perms LIKE 'djs:applet:warehouse:%')
  AND r.del_flag = '0'
  AND r.role_key IN ('warehouse_admin', 'warehouse_worker');

-- 5. 门店域 → store_admin / store_clerk
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r CROSS JOIN sys_menu m
WHERE (m.perms LIKE 'djs:store:%' OR m.perms LIKE 'djs:applet:store:%')
  AND r.del_flag = '0'
  AND r.role_key IN ('store_admin', 'store_clerk');

-- 6. 跨域通用（主数据 / OSS / applet 通用 picker）→ 全部业务角色
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r CROSS JOIN sys_menu m
WHERE (m.perms LIKE 'djs:common:%'
       OR m.perms LIKE 'djs:oss:%'
       OR m.perms LIKE 'djs:applet:common:%'
       OR m.perms LIKE 'djs:applet:oss:%')
  AND r.del_flag = '0'
  AND r.role_id <> 1;
