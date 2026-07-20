-- ============================================================
-- FIX-PLT-AD-D22-TESTFIX 农事 / 采摘菜单扁平化 + 清理改抽屉后的多余详情路由菜单
--
-- 测试问题 5：农事 / 采摘不应有更小的子菜单，原型上是平的。
-- 配合「详情/录入改 el-dialog/el-drawer（§6.13）」清理不再使用的整页路由菜单。
--
-- 改动三块：
--   1. 农事扁平：农事记录 8098 直挂农事管理 8007；灾害记录 8100 调序；
--      农事录入 8090 wrapper 隐藏（保留其 mp 权限 F 子节点）。
--   2. 采摘扁平：采摘计划列表 8082 提为唯一「采摘计划」直挂采摘管理 8004；
--      删 wrapper 8081；采摘计划调整 8083 由路由菜单改为 F 按钮权限挂 8082 下
--      （列表「调整」按钮 v-hasPermi=djs:plant:pick:adjust 仍要，故不能 DELETE）。
--   3. 删总览作物详情路由菜单 8113（改抽屉后路由废弃；总览查看权限是 8114，不依赖 8113）。
--
-- 守约定：不改旧 seed，删除前先删 sys_role_menu 对应行。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 农事扁平
--    农事管理 8007 ▸ 农事记录 8098（直挂）/ 灾害记录 8100
--    农事录入 8090 wrapper 隐藏（visible='1'），保留其 mp 权限 F 子节点
-- ------------------------------------------------------------
-- 1.1 农事记录 8098：从 8090 提到 8007 直挂，order_num=1
--     path 仍为 records，挂到 8007（path=plt-farm-work）后整路由为 /plt-farm-work/records，
--     component 仍 djs-plant/work/records/index，不改。
UPDATE sys_menu SET parent_id = 8007, order_num = 1 WHERE menu_id = 8098;

-- 1.2 灾害记录 8100：order_num=2（parent 已是 8007，不动）
UPDATE sys_menu SET order_num = 2 WHERE menu_id = 8100;

-- 1.3 农事录入 8090 wrapper：admin 导航不显（visible='1' = 隐藏），保留其 mp 权限 F 子节点
UPDATE sys_menu SET visible = '1' WHERE menu_id = 8090;

-- ------------------------------------------------------------
-- 2. 采摘扁平
--    采摘管理 8004 ▸ 采摘活动 8084 / 采摘计划 8082（直挂）
-- ------------------------------------------------------------
-- 2.1 采摘活动 8084：order_num=1
UPDATE sys_menu SET order_num = 1 WHERE menu_id = 8084;

-- 2.2 采摘计划列表 8082：提为唯一「采摘计划」菜单 → 直挂 8004，改名，order_num=2
--     component 仍 djs-plant/pick/plan/index；path=plan 挂到 8004（path=pick）后整路由 /pick/plan，不动。
UPDATE sys_menu SET parent_id = 8004, menu_name = '采摘计划', order_num = 2 WHERE menu_id = 8082;

-- 2.3 采摘计划调整 8083：路由菜单 → F 按钮权限，挂到 8082 下，清 path/component，保留 perms
--     （列表「调整」按钮 v-hasPermi=djs:plant:pick:adjust 仍依赖此权限，故改类型而非 DELETE）
UPDATE sys_menu
SET parent_id  = 8082,
    menu_name  = '调整',
    menu_type  = 'F',
    path       = '',
    component  = '',
    is_frame   = '1',
    is_cache   = '0',
    visible    = '1',
    order_num  = 1,
    perms      = 'djs:plant:pick:adjust',
    icon       = '#'
WHERE menu_id = 8083;

-- 2.4 删 wrapper 8081（其唯一有意义子 8082 已提走）：先 role_menu 后 menu
DELETE FROM sys_role_menu WHERE menu_id = 8081;
DELETE FROM sys_menu      WHERE menu_id = 8081;

-- ------------------------------------------------------------
-- 3. 删总览作物详情路由菜单 8113（改抽屉后废弃；总览查看权限是 8114，不依赖 8113）
--    先 role_menu 后 menu
-- ------------------------------------------------------------
DELETE FROM sys_role_menu WHERE menu_id = 8113;
DELETE FROM sys_menu      WHERE menu_id = 8113;
