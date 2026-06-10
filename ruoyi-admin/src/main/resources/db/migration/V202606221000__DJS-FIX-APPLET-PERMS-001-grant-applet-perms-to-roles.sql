-- DJS-FIX-APPLET-PERMS-001：修复 mp applet 权限从未真正授给业务角色的系统性 seed bug
--
-- 背景
--   历史 applet 权限 seed 用模糊匹配挂角色：
--     role_key  LIKE '%mp%' / '%applet%' / '%staff%' / '%employee%'
--     role_name LIKE '%员工%' / '%小程序%'
--   但真实业务角色是 boss / manager / breed_admin / breed_worker / vet /
--   warehouse_admin / warehouse_worker / plant_admin / plant_worker /
--   store_admin / store_clerk —— 一个都匹配不上，导致这批 djs:applet:* 权限
--   实际授给了 0 个角色（如 djs:applet:pig:search / BRD-LIST-001 等）。
--
--   过去全靠 mock 账号 LoginUser.menuPermission = {'*:*:*'} 通配掩盖；真微信登录改用
--   PermissionService 拿真实菜单权限后，所有走这类 seed 的 mp 接口对真用户全部 403
--   （前端弹"没有访问权限"）。
--
--   附带：system_admin(role_id=101) 并非通配角色（SYS-AUTH-001 仅给它挂 5000/5050/5051
--   三个通用主数据菜单），多个旧 seed 注释"system_admin perms 通配、无需挂"的假设是错的。
--   只有 ruoyi 自带 superadmin(role_id=1) 才是 *:*:* 通配。
--
-- 修复
--   把所有 djs:applet:* 权限菜单补授给除 superadmin(role_id=1) 外的全部业务角色。
--   mp 端访问控制的真正闸门是 role-tabs 板块可见性（UserBoardController 按角色返回可进
--   板块）；端点级 djs:applet:* 在 V1 从未做差异化（mock 全 *:*:*），故统一补授全量。
--   INSERT IGNORE 与既有正确 seed（NOT IN (1,101) / 显式 role_key IN）幂等共存。
--
-- 影响范围：sys_role_menu 增量补行。已登录用户需重新登录（menuPermission 在颁 token 时
--          由 PermissionService 实时读取，无缓存；re-login 即刷新）。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE m.perms LIKE 'djs:applet:%'
  AND r.role_id <> 1
  AND r.del_flag = '0';
