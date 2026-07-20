-- FIX-PLT-DASH-PERM-001 种植看板权限补授（决策 D-FIX-6 B2）。
--
-- 根因：种植看板 perm 'djs:plant:dashboard:view' 拆在独立 F 行 8111；原 PLT-DASH-001 DDL
--   (V202606150710) 仅授 8110、从不授 8111，且其授予来源 8010（地块管理）在 PLT-MD-001 seed
--   里只挂超管（role_id=1）→ 非超管业务角色既没拿到 8110 也没拿到 8111 → 调
--   /djs/plant/dashboard/summary | /gantt 被 @SaCheckPermission 拦 403 → 看板空白。
--
-- 修复：把 8110 + 8111 都补授给「持种植权限」的角色。授予来源对齐种植父菜单 8000
--   （W22-006 仓库看板授予范式）。8000 当前持有者 = role 102 boss / 103 manager / 105 plant_admin
--   （SYS-AUTH-001 seed），均为应见种植看板的非超管业务角色；超管 role_id=1 绕过鉴权不依赖此。
--   原 8010 来源弃用（8010 仅授超管，命中不到业务角色）。
--
-- 幂等：INSERT IGNORE，重复跑/重建库均安全。本 DDL 不新增 menu_id，仅补 sys_role_menu 关联行。
-- 生效：非超管账号需重新登录（重新加载 /getRouters + perm）后看板可见、取数 200。

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 8110 FROM sys_role_menu WHERE menu_id = 8000;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 8111 FROM sys_role_menu WHERE menu_id = 8000;
