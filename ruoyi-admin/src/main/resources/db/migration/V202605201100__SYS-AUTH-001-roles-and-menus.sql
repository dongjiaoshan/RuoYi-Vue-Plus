-- =============================================================================
-- SYS-AUTH-001  用户 / 角色 / 权限三件套
-- =============================================================================
--   1. 13 个角色种子数据（ruoyi 自带 superadmin role_id=1 + 12 个新增 djs 角色 role_id=101-112）
--   2. 业务一级目录菜单 + 二级通用主数据菜单 seed（menu_id 段 5000-5099 / 7000 / 8000 / 9000 / 10000）
--   3. 角色 → 菜单 / 角色 → 用户 关联种子（仅 boss / manager 全菜单；其他业务角色由各业务 ticket 自己 INSERT sys_role_menu）
--   4. admin (user_id=1) 关联 superadmin 角色（IGNORE 防重）；租户清理由 V202605201500 处理
--
-- 角色清单（按 doc/05 §4.4.4 表 + doc/06 SYS-AUTH-001 §5 + doc/02 v1.2 §SYS-AUTH-001）：
--   role_id=1   superadmin       超级管理员   （ruoyi 自带，不变）
--   role_id=101 system_admin     系统管理员
--   role_id=102 boss             老板         （v1.2 新增）
--   role_id=103 manager          管理人员     （v1.2 新增）
--   role_id=104 breed_admin      养殖管理员
--   role_id=105 plant_admin      种植管理员
--   role_id=106 warehouse_admin  仓库管理员
--   role_id=107 store_admin      门店管理员
--   role_id=108 breed_worker     养殖工人
--   role_id=109 vet              兽医
--   role_id=110 warehouse_worker 仓库工人（含分割师 / 库管员）
--   role_id=111 plant_worker     种植工人
--   role_id=112 store_clerk      门店店员
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 12 个新角色 INSERT（ruoyi superadmin role_id=1 是自带，不重复插）
-- -----------------------------------------------------------------------------
INSERT INTO sys_role
  (role_id, tenant_id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly,
   status, del_flag, create_by, create_time, remark)
VALUES
  (101, '1001', '系统管理员',     'system_admin',     2,  '1', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 用户/角色/字典维护'),
  (102, '1001', '老板',           'boss',             3,  '1', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 v1.2 DSH驾驶舱可见 + 跨域数据浏览'),
  (103, '1001', '管理人员',       'manager',          4,  '1', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 v1.2 DSH驾驶舱可见 + 业务管理'),
  (104, '1001', '养殖管理员',     'breed_admin',      5,  '1', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 养殖 admin 模块'),
  (105, '1001', '种植管理员',     'plant_admin',      6,  '1', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 种植 admin 模块'),
  (106, '1001', '仓库管理员',     'warehouse_admin',  7,  '1', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 仓库 admin 模块'),
  (107, '1001', '门店管理员',     'store_admin',      8,  '1', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 门店 admin 模块'),
  (108, '1001', '养殖工人',       'breed_worker',     9,  '4', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 养殖小程序'),
  (109, '1001', '兽医',           'vet',              10, '4', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 养殖小程序（含药品）'),
  (110, '1001', '仓库工人',       'warehouse_worker', 11, '4', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 仓库小程序（含分割师/库管员）'),
  (111, '1001', '种植工人',       'plant_worker',     12, '4', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 种植小程序'),
  (112, '1001', '门店店员',       'store_clerk',      13, '4', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 门店小程序');

-- -----------------------------------------------------------------------------
-- 2. 业务一级 / 二级目录菜单 seed
--    menu_id 段：通用主数据 5000-5099 / 养殖 7000 / 种植 8000 / 仓库 9000 / 门店 10000
--    一级目录是各业务 ticket 实现 list/form 时的父节点占位；具体 list 菜单由对应 ticket INSERT
-- -----------------------------------------------------------------------------
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
   menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  -- 一级目录：通用主数据（人员 / 门店 / 供应商 / 农场切换）
  (5000, '通用主数据',   0,    50,  'djs-common',    NULL,                            '', 1, 0, 'M', '0', '0', '',                          'tab',       1, NOW(), 'SYS-AUTH-001 通用主数据目录'),
  -- 二级（人员 / 门店 / 供应商三个 list 由 SYS-MD-001/002/003 自己 INSERT；本 ticket 不预占）

  -- 一级目录：养殖（BRD-*）
  (7000, '养殖',         0,    70,  'djs-breed',     NULL,                            '', 1, 0, 'M', '0', '0', '',                          'tree',      1, NOW(), 'SYS-AUTH-001 BRD-* 全域'),
  -- 一级目录：种植（PLT-*）
  (8000, '种植',         0,    80,  'djs-plant',     NULL,                            '', 1, 0, 'M', '0', '0', '',                          'tree-table',1, NOW(), 'SYS-AUTH-001 PLT-* 全域'),
  -- 一级目录：仓库（WMS-*）
  (9000, '仓库',         0,    90,  'djs-warehouse', NULL,                            '', 1, 0, 'M', '0', '0', '',                          'list',      1, NOW(), 'SYS-AUTH-001 WMS-* 全域'),
  -- 一级目录：门店销售（STR-* + TRC + DSH）
  (10000,'门店销售',     0,    100, 'djs-store',     NULL,                            '', 1, 0, 'M', '0', '0', '',                          'star',      1, NOW(), 'SYS-AUTH-001 STR-* + TRC-* + DSH-*');

-- -----------------------------------------------------------------------------
-- 3. 农场切换 perm seed（菜单不挂，仅做权限串占位，UserFarmController 端点用）
--    放在通用主数据下作"按钮型"权限（visible='1' 隐藏）
-- -----------------------------------------------------------------------------
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
   menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (5050, '查询可访问农场', 5000, 1, '', NULL, '', 1, 0, 'F', '1', '0', 'djs:user:farm:query',  '#', 1, NOW(), 'SYS-AUTH-001 农场切换器 GET /djs/user/farm/accessible'),
  (5051, '切换当前农场',   5000, 2, '', NULL, '', 1, 0, 'F', '1', '0', 'djs:user:farm:switch', '#', 1, NOW(), 'SYS-AUTH-001 农场切换器 POST /djs/user/farm/switch');

-- -----------------------------------------------------------------------------
-- 4. 角色 → 菜单 映射：boss / manager / system_admin / 4 业务 admin 给一级目录可见
--    （具体 list 菜单与按钮权限由对应业务 ticket INSERT sys_role_menu）
-- -----------------------------------------------------------------------------

-- boss / manager 全菜单可见（5000-10999 + 5050/5051 农场切换）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 102, menu_id FROM sys_menu WHERE menu_id BETWEEN 5000 AND 10999;
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 103, menu_id FROM sys_menu WHERE menu_id BETWEEN 5000 AND 10999;

-- system_admin 仅通用主数据目录可见
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (101, 5000), (101, 5050), (101, 5051);

-- 4 个业务 admin 各自仅可见对应一级目录 + 通用主数据（数据来源）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (104, 7000), (104, 5000), (104, 5050), -- breed_admin: 养殖 + 通用主数据 + 农场查询
  (105, 8000), (105, 5000), (105, 5050), -- plant_admin
  (106, 9000), (106, 5000), (106, 5050), -- warehouse_admin
  (107, 10000),(107, 5000), (107, 5050); -- store_admin

-- 5 个 worker 角色（breed_worker / vet / warehouse_worker / plant_worker / store_clerk）
-- V1 主要走小程序，admin 后台仅给农场查询权限（小程序登录后查可见农场）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (108, 5050), -- breed_worker
  (109, 5050), -- vet
  (110, 5050), -- warehouse_worker
  (111, 5050), -- plant_worker
  (112, 5050); -- store_clerk

-- -----------------------------------------------------------------------------
-- 5. 给 admin 用户分配 superadmin 角色（ruoyi 自带，sys_user_role 应已存在，IGNORE 防重）
--    （admin tenant_id 与 sys_tenant 重命名由 V202605201500__SYS-CLEANUP-single-tenant 统一处理）
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- =============================================================================
-- 验收 query（subagent 自测用）
--   SELECT role_id, role_key, role_name FROM sys_role WHERE role_id IN (1,101,102,103,104,105,106,107,108,109,110,111,112);
--   SELECT menu_id, menu_name, parent_id FROM sys_menu WHERE menu_id BETWEEN 5000 AND 10999;
--   SELECT role_id, COUNT(menu_id) FROM sys_role_menu WHERE role_id BETWEEN 101 AND 112 GROUP BY role_id;
--   SELECT user_id, user_name, tenant_id FROM sys_user WHERE user_id = 1;
-- =============================================================================
