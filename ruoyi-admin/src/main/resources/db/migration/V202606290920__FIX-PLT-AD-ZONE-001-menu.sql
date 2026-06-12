-- ============================================================
-- FIX-PLT-AD-ZONE-001  片区/地块菜单拆分 + djs_plot_lease 字典
-- ============================================================
-- 背景：原型「种植信息管理」下片区管理 / 地块管理是两个并列一等公民列表页，
--   当前 8010『片区与地块』把二者塞进双栏单页（左片区树 + 右地块表）。
--   决策#3 拍板：拆回两个独立菜单/路由。
--
-- 本迁移做三件事（守 menu_id 种植域 8000-8999）：
--   1. UPDATE 8010 menu_name『片区与地块』→『地块管理』（复用现 plot 页 + plot:* 权限不变）。
--   2. INSERT 8018『片区管理』（parent 8005 种植信息管理，component djs-plant/zone/index，
--      perms djs:plant:plot:list）；片区按钮 8011-8013 从 8010 re-parent 到 8018。
--   3. sys_role_menu：把已持有 8010 的角色补授 8018（业务角色看得到片区管理新菜单）。
--   4. 新建 djs_plot_lease 字典（1=自用 / 2=租赁，按 ADR-0004），供 PlotForm「是否租赁」枚举。
--
-- 锚点：以 menu_id 为准 UPDATE（前序 V202606171100 已把 8010 re-parent 到 8005）。
-- T3：跑完必 flush redis（bash script/sql/djs/_post-init.sh），否则左侧菜单 / 字典下拉不刷新。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 8010：片区与地块 → 地块管理（留给地块独立列表页，权限 / component path 不变）
--    order_num=2（片区管理排前；作物管理 8020 后移到 3）
-- ------------------------------------------------------------
UPDATE sys_menu
SET menu_name = '地块管理', path = 'plot', component = 'djs-plant/plot/index',
    perms = 'djs:plant:plot:list', menu_type = 'C', visible = '0', order_num = 2
WHERE menu_id = 8010;

-- ------------------------------------------------------------
-- 2. 8018：新增『片区管理』独立 C 菜单（parent 8005 种植信息管理，order_num=1 排在地块前）
--    复用 djs:plant:plot:list 读权限（与原 seed 一致）；片区写权限走 8011-8013 zone:*
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu
    (menu_id, menu_name, parent_id, order_num,
     path, component, query_param,
     is_frame, is_cache, menu_type, visible, status,
     perms, icon, create_by, create_time, remark)
VALUES
    (8018, '片区管理', 8005, 1, 'zone', 'djs-plant/zone/index', '',
     1, 0, 'C', '0', '0',
     'djs:plant:plot:list', 'tree', 1, NOW(), 'FIX-PLT-AD-ZONE-001');

-- 片区 3 按钮权限（zone add/edit/remove）从 8010 re-parent 到 8018
UPDATE sys_menu SET parent_id = 8018 WHERE menu_id IN (8011, 8012, 8013);

-- 作物管理 8020 / 土地认证 8050 / 果蔬认证 8060 顺位后移（片区(1)/地块(2) 占前两位）
UPDATE sys_menu SET order_num = 3 WHERE menu_id = 8020;
UPDATE sys_menu SET order_num = 4 WHERE menu_id = 8050;
UPDATE sys_menu SET order_num = 5 WHERE menu_id = 8060;

-- ------------------------------------------------------------
-- 3. sys_role_menu：把已持有 8010 的角色补授 8018（超管含在内；业务角色 102-112 等）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 8018 FROM sys_role_menu WHERE menu_id = 8010;

-- ------------------------------------------------------------
-- 4. djs_plot_lease 字典（1=自用 / 2=租赁，ADR-0004 djs 字典 seed 规范）
--    dict_id 103004（紧随 103003 所属大区）；dict_data 1030040/1030041
-- ------------------------------------------------------------
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
SELECT 103004, '1001', '是否租赁', 'djs_plot_lease', 1, NOW(), 'FIX-PLT-AD-ZONE-001 地块是否租赁（1=自用 / 2=租赁）'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'djs_plot_lease');

INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
SELECT 1030040, '1001', 0, '自用', '1', 'djs_plot_lease', '', 'info',    'Y', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'djs_plot_lease' AND dict_value = '1');

INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
SELECT 1030041, '1001', 1, '租赁', '2', 'djs_plot_lease', '', 'warning', 'N', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'djs_plot_lease' AND dict_value = '2');
