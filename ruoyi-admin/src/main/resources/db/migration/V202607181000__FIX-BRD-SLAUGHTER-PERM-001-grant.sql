-- FIX-BRD-SLAUGHTER-PERM-001 — 补出栏录入写权限授权（R42：真微信用户出栏「提交失败」403）。
--
-- 根因：SlaughterController.record（POST /djs/breed/event/slaughter）标
--   @SaCheckPermission("djs:breed:event:slaughter")，但 V202605281300__BRD-EVENT-004-be-menu.sql
--   仅建/授了只读 list 权限 djs:breed:event:slaughter:list（见该文件第 51 行），从未给写权限
--   djs:breed:event:slaughter 建 sys_menu 行、也未写 sys_role_menu。该缺口对【mock 登录】不暴露
--   （buildMockLoginUser 置 menuPermission=*:*:* 通配），但【真微信登录】走真权限
--   （WechatLoginServiceImpl → PermissionService.getMenuPermission），缺该串 → Sa-Token 抛
--   NotPermissionException → HTTP 403 → mp toast「提交失败」。与 FIX-BRD-CASTRATE-PERM-001 同型缺漏。
--
-- 修法：出栏仅 mp 端有提交入口（admin 不开），按「纯 perms 占位」范式建 F 型按钮菜单
--   （menu_type='F' + visible='1' 隐藏 + parent_id=0，不进 admin 路由树，避免后台侧栏多出无页面菜单）。
--   perms 串与 SlaughterController.record @SaCheckPermission 逐字一致：djs:breed:event:slaughter。
--   授给 boss/manager/breed_admin/breed_worker/vet 5 角色（与 V202605281300 死亡/淘汰/转移同口径）。
-- 注：ruoyi 约定 visible '0'=显示 / '1'=隐藏；perms 授权与 visible 无关，纯由 sys_role_menu 决定。
-- 授权后【真微信用户需重新登录】（token 的 menuPermission 是登录态快照）；admin 在跑则跑
--   script/sql/djs/_post-init.sh flush redis。纯 SQL，Flyway 自动应用，无需 mvn install。
-- menu_id 7153：落 7150-7199「业务事件扩展预留」（CLAUDE.md §6 养殖域二级分段；7150/7151 已被
--   FIX-BRD-CASTRATE-PERM-001 占用，7152/7153 空闲，取 7153）。
-- V202607181000 > 当前 flyway max V202607031300，无版本碰撞。

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 出栏录入写权限（mp POST /djs/breed/event/slaughter）
  (7153, '出栏录入', 0, 93, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:breed:event:slaughter', '#', 1, NOW(), 'FIX-BRD-SLAUGHTER-PERM-001');

-- 授权给养殖相关 5 角色（与 V202605281300 死亡/淘汰/转移同口径；INSERT IGNORE 幂等）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 7153 AS menu_id) m
WHERE r.role_key IN ('boss', 'manager', 'breed_admin', 'breed_worker', 'vet')
  AND r.del_flag = '0';
