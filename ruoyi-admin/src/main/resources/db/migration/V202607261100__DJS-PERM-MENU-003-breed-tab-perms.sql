-- DJS-PERM-MENU-003：养殖小程序(11020) 权限树收成 tab 级（菜单级 RBAC）。
--
-- 背景
--   养殖小程序下平铺了 28 个颗粒度权限（配种查询/录入、分娩查询/录入、饲料领用/退回/损耗、picker…），
--   配角色时一眼看不清、要逐个勾。客户要求：每个 mp 板块只暴露「底栏 tab」级权限（养殖工人懂的口径），
--   有权限就显示该 tab，没权限就隐藏。养殖 mp 底栏 4 tab = 养殖 / 疫苗药品 / 养殖管理 / 我的。
--
-- 机制（零 controller 改动、零 403 风险）
--   Sa-Token 鉴权用 vagueMatch 通配匹配（超管 `*:*:*` 即靠此匹配 4 段权限串）。给角色授一个通配 tab-perm
--   （如 `djs:breed:event:*`）即覆盖该 tab 下全部端点的 @SaCheckPermission，端点鉴权串一概不动。
--   mp 底栏 store.ts 按 `permissions.some(p => userPerms.includes(p))` 精确匹配——把 tab 的 permissions
--   设成这里授的通配串即可（见 config.ts breedTabbarList）。
--
-- 养殖 tab 覆盖 3 个权限族（事件/基础数据各一通配；猪只 picker djs:applet:pig:* 仍在通用小程序 11010，不动）：
--   djs:breed:event:*   配种/分娩/查情·断奶·返空/转移/死亡/淘汰/引种/耳标/生长/阉割/出栏（与 admin 事件页共用 controller）
--   djs:applet:breed:*  饲料领用·退回·损耗 / 栋舍·栏位·分娩·生长 picker（mp 专用）
-- 疫苗药品 tab：djs:breed:med*（一条前缀通配盖 med / med-batch / med-record / med-usage 四族）
-- 养殖管理 tab：djs:breed:dashboard:*（activity/aggregate/annual/inventory/monthly）
-- 我的 tab：人人可见，不设权限（与仓库小程序「我的」一致；gate 它会把退出/切板块入口也藏掉）。
--
-- 生效：menu 改动角色弹框树为实时 DB 查询，刷新即见；mp 端 PermissionService 颁 token 实时读 DB，重新登录即生效。无需 flush redis。

-- 1) 归位：引种登记(7100)/仔猪耳标(7110)/生长记录(7140) 是 admin C 菜单（带 admin CRUD F 子 7101-7104/7111-7112/7141-7144），
--    当初为给 mp 借权限被迁进 mp 树。现 mp 由通配 tab-perm 覆盖，把它们迁回 admin 养殖(7000)，perms/路由/授权不变，F 子随父走。
UPDATE sys_menu SET parent_id = 7000 WHERE menu_id IN (7100, 7110, 7140);

-- 2) 新建养殖 mp tab 权限节点（11021-11025，11000-11999 mp 权限段内；visible='1' 仅授权树可见、不进侧边栏）
--    养殖=M 容器（path 非空避免 /getRouters 下发空 path 崩 vue-router）；其余为 F 型纯权限节点（按钮不生成路由，path 可空）。
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
  (11021, '养殖',          11020, 1, 'mp-breed-home', NULL, '', 1, 0, 'M', '1', '0', '',                       'tree', 1, NOW(), 'DJS-PERM-MENU-003 养殖 mp 底栏 tab：养殖'),
  (11022, '养殖·生产事件', 11021, 1, '',              '',   '', 1, 0, 'F', '1', '0', 'djs:breed:event:*',      '#',    1, NOW(), 'DJS-PERM-MENU-003 配种/分娩/查情·断奶·返空/转移/死亡/淘汰/引种/耳标/生长/阉割/出栏'),
  (11023, '养殖·基础数据', 11021, 2, '',              '',   '', 1, 0, 'F', '1', '0', 'djs:applet:breed:*',     '#',    1, NOW(), 'DJS-PERM-MENU-003 饲料领用·退回·损耗 / 栋舍·栏位·分娩·生长 picker'),
  (11024, '疫苗药品',      11020, 2, '',              '',   '', 1, 0, 'F', '1', '0', 'djs:breed:med*',         '#',    1, NOW(), 'DJS-PERM-MENU-003 养殖 mp 底栏 tab：疫苗药品（med/med-batch/med-record/med-usage）'),
  (11025, '养殖管理',      11020, 3, '',              '',   '', 1, 0, 'F', '1', '0', 'djs:breed:dashboard:*',  '#',    1, NOW(), 'DJS-PERM-MENU-003 养殖 mp 底栏 tab：养殖管理（dashboard）');

-- 3) 授权：沿用旧 11020 颗粒度权限的角色集（养殖相关 6 角色；超管 role_id=1 走 *:*:* 通配无需挂行）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r CROSS JOIN sys_menu m
WHERE r.role_id IN (101, 102, 103, 104, 108, 109)
  AND m.menu_id IN (11021, 11022, 11023, 11024, 11025);

-- 4) 删除 mp 专用颗粒度叶子（perms 已被上面通配 tab-perm 覆盖）+ 其授权行。
--    含：mp 事件查询/录入 7120-7135、阉割 7150/7151、出栏 7153、饲料 7361-7365、分娩/栋舍/栏位/生长 picker 5353-5357。
--    不含：7100/7110/7140（已迁回 admin）、7360（通用 pig picker，在 11010）。
DELETE FROM sys_role_menu WHERE menu_id IN (
  7120, 7121, 7122, 7123, 7124, 7125, 7130, 7131, 7132, 7133, 7134, 7135,
  7150, 7151, 7153,
  7361, 7362, 7363, 7364, 7365,
  5353, 5354, 5355, 5356, 5357);
DELETE FROM sys_menu WHERE menu_id IN (
  7120, 7121, 7122, 7123, 7124, 7125, 7130, 7131, 7132, 7133, 7134, 7135,
  7150, 7151, 7153,
  7361, 7362, 7363, 7364, 7365,
  5353, 5354, 5355, 5356, 5357);

-- 验证（手动）：
-- SELECT menu_id, menu_name, menu_type, perms FROM sys_menu WHERE parent_id IN (11020, 11021) ORDER BY parent_id, order_num;
--   期望 11020 → 养殖(11021)/疫苗药品(11024)/养殖管理(11025)；11021 → 生产事件(11022)/基础数据(11023)。
