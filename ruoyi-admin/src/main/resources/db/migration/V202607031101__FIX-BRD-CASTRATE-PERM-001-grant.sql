-- FIX-BRD-CASTRATE-PERM-001 — 补阉割录入权限授权（邓博 2026-06-17 #39：阉割录入数据失败）。
--
-- 根因：CastrateController @SaCheckPermission("djs:breed:event:castrate")，但
--   V202605281300__BRD-EVENT-004-be-menu.sql 当时按「mp V1 登录态默认全角色」的假设
--   故意未给 castrate 建菜单/授权（注释见该文件尾部）。该假设对【mock 登录】成立
--   （buildMockLoginUser 置 menuPermission=*:*:*），但【真微信登录】走
--   WechatLoginServiceImpl.issueToken → PermissionService.getMenuPermission(userId)，
--   实填真权限（非通配），缺 djs:breed:event:castrate → Sa-Token 抛 NotPermissionException
--   → HTTP 403 → mp toast「提交失败，请稍后重试」。die/transfer/eliminate/slaughter 已在
--   V202605281300 授权故正常，唯阉割漏授。
--
-- 修法：阉割仅 mp 端有入口（admin 不开），按「纯 perms 占位」范式建 F 型按钮菜单
--   （沿 V202607021100 mp 生长录入范式：menu_type='F' + visible='1' 隐藏 + parent_id=0，
--   不进 admin 路由树，避免在后台侧栏多出无页面的破损菜单）。两个 perms 串与
--   CastrateController @SaCheckPermission 逐字一致：record→djs:breed:event:castrate /
--   list→djs:breed:event:castrate:list。授给 boss/manager/breed_admin/breed_worker/vet 5 角色。
-- 注：ruoyi 约定 visible '0'=显示 / '1'=隐藏；perms 授权与 visible 无关，纯由 sys_role_menu 决定。
-- 授权后【真微信用户需重新登录】（token 的 menuPermission 是登录态快照）；admin 在跑则
--   跑 script/sql/djs/_post-init.sh flush redis。纯 SQL，Flyway 自动应用，无需 mvn install。
-- menu_id 段 7150-7151：落 7150-7199「业务事件扩展预留」（CLAUDE.md §6 养殖域二级分段）。
-- V202607031101 > 当前 flyway max V202607031000，排在 V202607031100 之后无碰撞。

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 阉割列表权限（mp GET /djs/breed/event/castrate/list）
  (7150, 'mp 阉割记录查询', 0, 90, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:breed:event:castrate:list', '#', 1, NOW(), 'FIX-BRD-CASTRATE-PERM-001'),
  -- 阉割录入权限（mp POST /djs/breed/event/castrate）
  (7151, 'mp 阉割录入', 0, 91, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:breed:event:castrate', '#', 1, NOW(), 'FIX-BRD-CASTRATE-PERM-001');

-- 授权给养殖相关 5 角色（与 V202605281300 死亡/淘汰/转移同口径；INSERT IGNORE 幂等）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 7150 AS menu_id UNION SELECT 7151) m
WHERE r.role_key IN ('boss', 'manager', 'breed_admin', 'breed_worker', 'vet')
  AND r.del_flag = '0';
