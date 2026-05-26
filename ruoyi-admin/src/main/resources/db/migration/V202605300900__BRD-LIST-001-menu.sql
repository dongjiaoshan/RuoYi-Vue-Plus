-- BRD-LIST-001 猪只 4 类列表（母 / 公 / 仔 / 育肥）+ 详情页 + PigPicker mp 端 perm
--
-- menu_id 段 7300-7399（养殖二级分段，CLAUDE.md §6 已固化给本 ticket）
--
-- 分配：
--   7300        母猪列表 父菜单的子 (C 菜单)
--   7310-7314   母猪 5 按钮权限（list / query / export 等）
--   7315-7319   预留
--   7320-7329   公猪列表 + 5 按钮
--   7330-7339   仔猪列表 + 5 按钮
--   7340-7349   育肥猪列表 + 5 按钮
--   7350-7359   猪只详情（独立路由 + 隐藏菜单）
--   7360-7369   mp 端 PigPicker 端点权限
--   7370-7399   批量选择 / 预留扩展
--
-- 注意：本 ticket 不删旧 menu 7200（BRD-CORE-001 合并版列表保留作为"事件 type 过滤"补充查询）；
--      doc/12 偏离审计 §S1-01 决策保留双菜单（4 独立 + 1 合并）。

-- ============================================================
-- 1. 4 类列表父菜单 (C 菜单) — parent_id=7000 养殖父菜单
-- ============================================================
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 7300 母猪列表
  (7300, '母猪列表', 7000, 11, 'pig-sow', 'djs-breed/pig/sow/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:pig:list', 'female', 1, NOW(), 'BRD-LIST-001 sow list'),

  -- 7320 公猪列表
  (7320, '公猪列表', 7000, 12, 'pig-boar', 'djs-breed/pig/boar/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:pig:list', 'male', 1, NOW(), 'BRD-LIST-001 boar list'),

  -- 7330 仔猪列表
  (7330, '仔猪列表', 7000, 13, 'pig-piglet', 'djs-breed/pig/piglet/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:pig:list', 'children', 1, NOW(), 'BRD-LIST-001 piglet list'),

  -- 7340 育肥猪列表
  (7340, '育肥猪列表', 7000, 14, 'pig-fattening', 'djs-breed/pig/fattening/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:pig:list', 'pig', 1, NOW(), 'BRD-LIST-001 fattening list');

-- ============================================================
-- 2. 4 类按钮权限（每类 5 按钮：query / export / 详情已在父级 djs:breed:pig:query；
--    复用现有 perm 字符串，避免给每个 vue 单独发 perm）
-- ============================================================
-- 母猪
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, menu_type, perms, status, visible, icon, create_by, create_time)
VALUES
  (7310, '母猪查询', 7300, 1, 'F', 'djs:breed:pig:list',   '0', '0', '#', 1, NOW()),
  (7311, '母猪详情', 7300, 2, 'F', 'djs:breed:pig:query',  '0', '0', '#', 1, NOW()),
  (7312, '母猪导出', 7300, 3, 'F', 'djs:breed:pig:export', '0', '0', '#', 1, NOW());

-- 公猪
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, menu_type, perms, status, visible, icon, create_by, create_time)
VALUES
  (7321, '公猪查询', 7320, 1, 'F', 'djs:breed:pig:list',   '0', '0', '#', 1, NOW()),
  (7322, '公猪详情', 7320, 2, 'F', 'djs:breed:pig:query',  '0', '0', '#', 1, NOW()),
  (7323, '公猪导出', 7320, 3, 'F', 'djs:breed:pig:export', '0', '0', '#', 1, NOW());

-- 仔猪
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, menu_type, perms, status, visible, icon, create_by, create_time)
VALUES
  (7331, '仔猪查询', 7330, 1, 'F', 'djs:breed:pig:list',   '0', '0', '#', 1, NOW()),
  (7332, '仔猪详情', 7330, 2, 'F', 'djs:breed:pig:query',  '0', '0', '#', 1, NOW()),
  (7333, '仔猪导出', 7330, 3, 'F', 'djs:breed:pig:export', '0', '0', '#', 1, NOW());

-- 育肥猪
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, menu_type, perms, status, visible, icon, create_by, create_time)
VALUES
  (7341, '育肥猪查询', 7340, 1, 'F', 'djs:breed:pig:list',   '0', '0', '#', 1, NOW()),
  (7342, '育肥猪详情', 7340, 2, 'F', 'djs:breed:pig:query',  '0', '0', '#', 1, NOW()),
  (7343, '育肥猪导出', 7340, 3, 'F', 'djs:breed:pig:export', '0', '0', '#', 1, NOW());

-- ============================================================
-- 3. 独立详情页（C 菜单 visible='1' 隐藏，仅作路由注册用）
-- ============================================================
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 7350 详情页（隐藏，但要在动态路由里注册，path 含 :id 占位）
  (7350, '猪只详情', 7000, 15, 'pig-detail/:id', 'djs-breed/pig/detail', '',
   1, 0, 'C', '1', '0',
   'djs:breed:pig:query', 'view', 1, NOW(), 'BRD-LIST-001 standalone detail');

-- ============================================================
-- 4. mp 端 PigPicker 端点权限 — 注册到 7360 段
--    (visible=1 隐藏；perms 走 djs:applet:pig:search；后端 PigAppletController 用)
-- ============================================================
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7360, 'mp 猪只 picker 搜索', 7000, 91, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:applet:pig:search', '#', 1, NOW(), 'BRD-LIST-001 PigPicker /applet/pig/search');

-- ============================================================
-- 5. 把 PigPicker 权限挂给 mp role（让小程序员工登录后能调 picker 端点）
--    role_id 1 = admin（默认全权限，不需手动挂）；
--    mp 端走 role_id 由 SYS-AUTH-001 / SYS-FIX-* 配的"应用员工"角色；
--    这里走 INSERT IGNORE + WHERE NOT EXISTS 的"兜底"方案：把 7360 挂给所有
--    name LIKE '%员工%' 或 '%mp%' 的非 admin role（精确范围由 closing 时审计）。
-- ============================================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 7360
FROM sys_role r
WHERE r.role_id <> 1
  AND r.del_flag = '0'
  AND (r.role_key LIKE '%mp%' OR r.role_key LIKE '%applet%' OR r.role_key LIKE '%staff%' OR r.role_key LIKE '%employee%' OR r.role_name LIKE '%员工%' OR r.role_name LIKE '%小程序%');
