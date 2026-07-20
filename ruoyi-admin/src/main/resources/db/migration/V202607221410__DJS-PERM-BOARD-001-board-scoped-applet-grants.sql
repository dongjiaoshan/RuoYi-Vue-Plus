-- DJS-PERM-BOARD-001：mp applet 权限从"人人全授"收敛为"按板块差异化授权"。
--
-- 背景
--   V202606221000 用 CROSS JOIN 把全部 djs:applet:* 权限授给所有非超管角色（人人全权），
--   mp 端的访问差异化当时只靠 UserBoardController 的板块可见性。现要落到「单页/按板块」
--   的真实端点权限：每个业务角色只持有自己板块的 applet 权限，跨板块端点 @SaCheckPermission 拦截。
--
-- 设计
--   1) 板块归属按 perms 前缀，但有两类跨域例外必须特判：
--      - 跨板块共享 picker（403 安全网，必须 broad 授给所有业务角色，否则消费它的别板块页拿 403）：
--          djs:applet:oss:*               OSS 上传凭证（所有编辑页）
--          djs:applet:common:*            门店 picker（门店 + 仓库打包/发货/退货）
--          djs:applet:pig:*               猪只 picker（养殖事件 + 仓库分拣 pigSelect + 门店需求）
--          djs:applet:warehouse:location:list   库位 picker
--          djs:applet:warehouse:product:list    产品 picker（门店需求 + 仓库盘点/退货/打包）
--      - 饲料 djs:applet:breed:feed:*：前缀是 breed 但养殖(喂养) + 仓库(物资库存) 共管 → breed + warehouse 双授。
--   2) admin 三件套（101 system_admin / 102 boss / 103 manager）保留全部 applet（看全部板块，
--      与 UserBoardController ADMIN_ROLES 一致），不在本迁移删改。
--   3) 超管 role_id=1 走框架 *:*:* 通配，sys_role_menu 无需挂行，不动。
--
-- 角色对照（role_id）
--   101 system_admin / 102 boss / 103 manager      → 全部（保留）
--   104 breed_admin / 108 breed_worker / 109 vet    → breed
--   105 plant_admin / 111 plant_worker              → plant
--   106 warehouse_admin / 110 warehouse_worker      → warehouse + 饲料
--   107 store_admin / 112 store_clerk               → store（V1 暂无 store:* applet 权限，仅 broad picker）
--
-- 生效
--   menuPermission 在颁 token 时由 PermissionService 实时读 DB（无缓存）：已登录用户重新登录即刷新，
--   无需 flush redis。

-- 1) 清掉业务角色(104-112)现有的全部 applet 授权（admin 三件套 101-103 不动，保留全授）
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON rm.menu_id = m.menu_id
WHERE m.perms LIKE 'djs:applet:%'
  AND rm.role_id IN (104, 105, 106, 107, 108, 109, 110, 111, 112);

-- 2) BROAD：跨板块共享 picker + OSS 授给所有业务角色(104-112)
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id IN (104, 105, 106, 107, 108, 109, 110, 111, 112)
  AND ( m.perms LIKE 'djs:applet:oss:%'
     OR m.perms LIKE 'djs:applet:common:%'
     OR m.perms LIKE 'djs:applet:pig:%'
     OR m.perms = 'djs:applet:warehouse:location:list'
     OR m.perms = 'djs:applet:warehouse:product:list' );

-- 3) 养殖板块角色(104 breed_admin / 108 breed_worker / 109 vet) → breed 全部
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id IN (104, 108, 109)
  AND m.perms LIKE 'djs:applet:breed:%';

-- 4) 种植板块角色(105 plant_admin / 111 plant_worker) → plant 全部
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id IN (105, 111)
  AND m.perms LIKE 'djs:applet:plant:%';

-- 5) 仓库板块角色(106 warehouse_admin / 110 warehouse_worker) → warehouse 全部 + 饲料(跨域共管)
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id IN (106, 110)
  AND ( m.perms LIKE 'djs:applet:warehouse:%'
     OR m.perms LIKE 'djs:applet:breed:feed:%' );

-- 6) 门店板块角色(107 store_admin / 112 store_clerk) → store 全部
--    （V1 暂无 djs:applet:store:* 权限，门店 mp 页靠 @SaCheckLogin + broad picker；此段为后续 store:* 预留）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id IN (107, 112)
  AND m.perms LIKE 'djs:applet:store:%';

-- 验证（按需手动跑）：每个业务角色 applet 权限条数
-- SELECT rm.role_id, COUNT(*) cnt
-- FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id = m.menu_id
-- WHERE m.perms LIKE 'djs:applet:%' AND rm.role_id BETWEEN 101 AND 112
-- GROUP BY rm.role_id ORDER BY rm.role_id;
