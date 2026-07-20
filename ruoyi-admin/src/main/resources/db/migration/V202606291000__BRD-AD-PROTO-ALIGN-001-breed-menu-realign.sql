-- ============================================================
-- BRD-AD-PROTO-ALIGN-001  admin 养殖侧边栏 IA 对齐原型
--
-- 原型权威 IA（甲方侧边栏）养殖侧只有两个分组：
--   育种配置管理（4 子：品种信息管理 / 品系信息管理 / 品种配种信息管理 / 品系配种信息管理）
--   农场信息管理（2 子：农场栋舍管理 / 生产配置管理）
-- 另保留 Kevin 指定的：猪只主表(7200) / 事件台账(7145)。其余移出侧边栏。
--
-- 最终态养殖侧边栏（visible='0' 的二级 C/M）：
--   猪只主表(7200)
--   育种配置管理(7010, M) → 品种信息管理 / 品系信息管理 / 品种配种信息管理 / 品系配种信息管理
--   农场信息管理(7005, M, 本文件新建) → 农场栋舍管理(7020) / 生产配置管理(7050)
--   事件台账(7145)
--
-- 本文件 6 段（全 UPDATE / INSERT IGNORE，幂等）：
--   1. 新建「农场信息管理」M 目录 7005（parent 7000），绑 102/103/104
--   2. reparent 7020 农场 + 7050 生产配置 到 7005 下
--   3. 改名 7010/7016-7019/7020/7050 对齐原型菜单名
--   4. 隐藏药品库 7060 整组（visible='1'，原型养殖侧无药品库）
--   5. 隐藏引种登记 7100 整组（visible='1'，原型养殖侧无引种登记）
--   6. 新建「栏位详情」隐藏路由 7023（克隆 7205 猪只详情模式，动态路由参数页）
--
-- 说明：visible='1' = 仅控侧边栏渲染不显示，role_menu / sa-token 权限不动，可逆。
--       菜单结构调整不动任何业务代码 / 权限串 / 旧 seed 文件。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 新建「农场信息管理」M 目录（menu_id=7005）
--    order_num=2：夹在 育种配置管理(7010, order=1) 与 事件台账(7145, order=4) 之间。
--    perms / component 空（M 目录无路由），icon 用 guide。
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7005, '农场信息管理', 7000, 2, 'farm-group', '', '',
   1, 0, 'M', '0', '0',
   '', 'guide', 1, NOW(), 'BRD-AD-PROTO-ALIGN-001 农场信息管理分组（原型 IA）');

-- 角色 → 菜单：boss(102) / manager(103) / breed_admin(104) 可见新分组目录
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 102, menu_id FROM sys_menu WHERE menu_id = 7005;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 103, menu_id FROM sys_menu WHERE menu_id = 7005;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 104, menu_id FROM sys_menu WHERE menu_id = 7005;

-- ------------------------------------------------------------
-- 2. reparent：农场栋舍(7020) + 生产配置(7050) 收到 7005 分组下
--    （它们的子按钮权限 parent_id 仍指向 7020/7050，不受影响）
-- ------------------------------------------------------------
UPDATE sys_menu SET parent_id = 7005 WHERE menu_id IN (7020, 7050);

-- ------------------------------------------------------------
-- 3. 改名（菜单名对齐原型，不改 path / component / perms）
-- ------------------------------------------------------------
UPDATE sys_menu SET menu_name = '育种配置管理'     WHERE menu_id = 7010;
UPDATE sys_menu SET menu_name = '品种信息管理'     WHERE menu_id = 7016;
UPDATE sys_menu SET menu_name = '品系信息管理'     WHERE menu_id = 7017;
UPDATE sys_menu SET menu_name = '品种配种信息管理' WHERE menu_id = 7018;
UPDATE sys_menu SET menu_name = '品系配种信息管理' WHERE menu_id = 7019;
UPDATE sys_menu SET menu_name = '农场栋舍管理'     WHERE menu_id = 7020;
UPDATE sys_menu SET menu_name = '生产配置管理'     WHERE menu_id = 7050;

-- ------------------------------------------------------------
-- 4. 隐藏药品库 7060 整组（目录 + 子按钮权限）
--    原型养殖侧无药品库；visible='1' 仅控侧边栏不渲染，权限 / 代码不动，可逆。
-- ------------------------------------------------------------
UPDATE sys_menu SET visible = '1' WHERE menu_id = 7060 OR parent_id = 7060;

-- ------------------------------------------------------------
-- 5. 隐藏引种登记 7100 整组（目录 + 子按钮权限）
--    原型养殖侧无引种登记（引种主入口在 mp 端）；visible='1' 可逆。
-- ------------------------------------------------------------
UPDATE sys_menu SET visible = '1' WHERE menu_id = 7100 OR parent_id = 7100;

-- ------------------------------------------------------------
-- 6. 新建「栏位详情」隐藏路由（克隆 7205 猪只详情模式）
--    menu_id=7023（农场段 7020-7029 内空闲；7021/7022 已被农场查询/编辑按钮占用）。
--    parent_id=7000（与 7205 一致：隐藏详情路由直接挂顶层养殖目录，path 含完整子路径，
--    full route = /djs-breed/farm/pen-detail/:barnId；若挂 7020 会路径重复 /farm/farm → 404）。
--    visible='1' 不进侧边栏；path 带 :barnId 动态参数让前端动态路由能注册。
--    复用 djs:breed:barn:list 权限，不新增权限串。
--    前端按此 path/component 建 vue：path=farm/pen-detail/:barnId  component=djs-breed/farm/pen-detail
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7023, '栏位详情', 7000, 16, 'farm/pen-detail/:barnId', 'djs-breed/farm/pen-detail', '',
   1, 0, 'C', '1', '0',
   'djs:breed:barn:list', '#', 1, NOW(),
   'BRD-AD-PROTO-ALIGN-001 栏位详情隐藏路由（克隆 7205 模式，parent=7000，path 参数 :barnId；visible=1 不进侧边栏）');

-- 绑给 boss(102) / manager(103) / breed_admin(104)（隐藏路由仍需在角色菜单集内才会为这些角色生成）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 102, menu_id FROM sys_menu WHERE menu_id = 7023;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 103, menu_id FROM sys_menu WHERE menu_id = 7023;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 104, menu_id FROM sys_menu WHERE menu_id = 7023;

-- ============================================================
-- ⚠️ 跑完本迁移后必须 flush redis 字典 / 菜单缓存：
--     bash script/sql/djs/_post-init.sh
--    （sys_menu 改动需用户重新登录后刷新生效）
-- ============================================================
