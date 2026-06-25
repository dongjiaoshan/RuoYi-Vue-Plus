-- DJS-PERM-MENU-005：仓库小程序(11040) 权限树收成 tab/二级菜单级（菜单级 RBAC）。机制同 DJS-PERM-MENU-003（Sa-Token 通配，零 controller 改动、零 403）。
--
-- 仓库比其他 mp 板块更细（客户要求保留二级菜单）：
--   蔬菜处理(11041) → 毛菜处理 / 果蔬月台（月台入库+蔬菜入库）
--   分拣发货(11042) → 物资领用 / 发货月台 / 库存盘点 / 退货管理
--   燎毛间(11068)   → 白条分割领用 / 打包·包材   （首页聚合，tabbar 该 tab 不 gate，节点仅整理授权树）
--   需求调度(11071) → 调度员功能（首页/白条·蔬菜需求/猪只选择/统计）
--
-- 通配覆盖（删颗粒度叶子后由这些通配覆盖端点 @SaCheckPermission）：
--   vegHandle:handle* / platform* / stockIn*  ·  mat:* / ship:* / check:* / return:*  ·  pigCut:* / pack*(含 packing) / demand:*

-- 1) 蔬菜处理(11041) 二级
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
  (11060, '毛菜处理', 11041, 1, '',                 '',   '', 1, 0, 'F', '1', '0', 'djs:applet:warehouse:vegHandle:handle*',   '#',    1, NOW(), 'DJS-PERM-MENU-005'),
  (11061, '果蔬月台', 11041, 2, 'mp-wh-veg-platform', NULL, '', 1, 0, 'M', '1', '0', '',                                         'tree', 1, NOW(), 'DJS-PERM-MENU-005'),
  (11062, '月台入库', 11061, 1, '',                 '',   '', 1, 0, 'F', '1', '0', 'djs:applet:warehouse:vegHandle:platform*', '#',    1, NOW(), 'DJS-PERM-MENU-005'),
  (11063, '蔬菜入库', 11061, 2, '',                 '',   '', 1, 0, 'F', '1', '0', 'djs:applet:warehouse:vegHandle:stockIn*',  '#',    1, NOW(), 'DJS-PERM-MENU-005');

-- 2) 分拣发货(11042) 二级（客户指定 4 菜单）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
  (11064, '物资领用', 11042, 1, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:warehouse:mat:*',    '#', 1, NOW(), 'DJS-PERM-MENU-005 领用/退回/损耗'),
  (11065, '发货月台', 11042, 2, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:warehouse:ship:*',   '#', 1, NOW(), 'DJS-PERM-MENU-005 清点/白条/蔬菜/礼盒/门店发货'),
  (11066, '库存盘点', 11042, 3, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:warehouse:check:*',  '#', 1, NOW(), 'DJS-PERM-MENU-005 多库一览/盘点明细/实盘录入/修改/删除'),
  (11067, '退货管理', 11042, 4, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:warehouse:return:*', '#', 1, NOW(), 'DJS-PERM-MENU-005 退货录入');

-- 3) 仓库小程序(11040) 直挂：燎毛间（首页聚合）+ 需求调度
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
  (11068, '燎毛间',     11040, 3, 'mp-wh-burn', NULL, '', 1, 0, 'M', '1', '0', '',                                  'tree', 1, NOW(), 'DJS-PERM-MENU-005 仓库首页聚合（燎毛/分割/打包）'),
  (11069, '白条分割领用', 11068, 1, '',          '',   '', 1, 0, 'F', '1', '0', 'djs:applet:warehouse:pigCut:*',     '#',    1, NOW(), 'DJS-PERM-MENU-005 白条领用/出库称重/完成'),
  (11070, '打包·包材',   11068, 2, '',          '',   '', 1, 0, 'F', '1', '0', 'djs:applet:warehouse:pack*',        '#',    1, NOW(), 'DJS-PERM-MENU-005 蔬菜/礼盒/干货/芹菜/白条打包 + 包材（pack* 含 packing）'),
  (11071, '需求调度',   11040, 4, '',          '',   '', 1, 0, 'F', '1', '0', 'djs:applet:warehouse:demand:*',     '#',    1, NOW(), 'DJS-PERM-MENU-005 调度首页/白条·蔬菜需求/猪只选择/统计');

-- 4) 授权：沿用当前仓库颗粒度权限的角色集
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r CROSS JOIN sys_menu m
WHERE r.role_id IN (101, 102, 103, 106, 108, 110)
  AND m.menu_id IN (11060, 11061, 11062, 11063, 11064, 11065, 11066, 11067, 11068, 11069, 11070, 11071);

-- 5) 删除颗粒度叶子（perms 已被上面通配覆盖）
--    蔬菜处理 9096-9098；分拣发货 9201/9261/9202/9262/9203/9263/9204/9264/9205/9265/9106/9206/9107/9108；
--    打包 9221-9225；包材 9247-9248；调度 9271-9275；白条领用 9082-9084。
SET @dead = '9096,9097,9098,9201,9202,9203,9204,9205,9206,9261,9262,9263,9264,9265,9106,9107,9108,9221,9222,9223,9224,9225,9247,9248,9271,9272,9273,9274,9275,9082,9083,9084';
DELETE FROM sys_role_menu WHERE FIND_IN_SET(menu_id, @dead);
DELETE FROM sys_menu      WHERE FIND_IN_SET(menu_id, @dead);
