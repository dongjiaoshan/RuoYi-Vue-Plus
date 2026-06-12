-- FIX-PLT-AD-OVERVIEW-001 新建种植总览菜单 + 删除旧种植看板菜单（决策#1）。
--
-- 种植域 menu_id 段 8000-8999；dashboard 子段 8110-8199（CLAUDE.md §6.6）。删 8110/8111（旧看板）
-- 后在同段内新占 8112-8114（种植总览 + 作物详情下钻 + view 权限），走新 Flyway 不改旧 seed。
-- 新菜单挂「种植管理」组 8006（V202606171100 定义），order_num=0 置于种植计划(8070)之前。

-- ============================ 删：旧种植看板 8110/8111 ============================
-- 含旧着陆置顶（V202606171100:124）/ perm 补授（V202606190900）残留，sys_role_menu + sys_menu 一并清。
DELETE FROM sys_role_menu WHERE menu_id IN (8110, 8111);
DELETE FROM sys_menu      WHERE menu_id IN (8110, 8111);

-- ============================ 建：种植总览 8112 + 作物详情 8113 + view 权限 8114 ============================
-- 8112 种植总览 C 菜单（component=djs-plant/overview/index，visible='0' 显示，order_num=0 置前）。
-- 8113 作物详情下钻 C 菜单（component=djs-plant/overview/cropDetail，visible='1' 隐藏，从卡片下钻进入）。
-- 8114 view F 权限（perms djs:plant:overview:view，挂 8112）。
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
   menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (8112, '种植总览', 8006, 0, 'overview', 'djs-plant/overview/index', '', 1, 0,
   'C', '0', '0', 'djs:plant:overview:view', 'chart', 1, NOW(), 'FIX-PLT-AD-OVERVIEW-001 种植总览着陆页'),
  (8113, '作物详情', 8006, 1, 'overview/crop-detail', 'djs-plant/overview/cropDetail', '', 1, 0,
   'C', '1', '0', 'djs:plant:overview:view', '#', 1, NOW(), 'FIX-PLT-AD-OVERVIEW-001 作物详情下钻（隐藏）'),
  (8114, '种植总览查看', 8112, 1, '', '', '', 1, 0,
   'F', '0', '0', 'djs:plant:overview:view', '#', 1, NOW(), 'FIX-PLT-AD-OVERVIEW-001 view 权限');

-- ============================ 授权：照 PLT-DASH-PERM 范式，来源种植父菜单 8000 ============================
-- 8000 当前持有者 = role 102 boss / 103 manager / 105 plant_admin（SYS-AUTH-001 seed）+ 超管绕过鉴权。
-- 8112/8113/8114 三行都补授，使种植总览与种植计划可见性一致。INSERT IGNORE 幂等。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 8112 FROM sys_role_menu WHERE menu_id = 8000;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 8113 FROM sys_role_menu WHERE menu_id = 8000;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 8114 FROM sys_role_menu WHERE menu_id = 8000;
