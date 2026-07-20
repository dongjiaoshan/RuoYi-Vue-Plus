-- DJS-PERM-MENU-002：仓库小程序(11040) 授权树加二级容器，按 mp tab 分层。
--
-- 背景
--   DJS-PERM-MENU-001 把仓库 mp 权限叶子全部平铺挂在「仓库小程序」(11040) 下。角色弹框授权树里
--   一眼看不出哪些 perm 对应哪个 mp tab。本迁移在 11040 下加 2 个二级容器，按小程序「蔬菜处理 /
--   分拣发货」两个底部 tab 把对应叶子归类，授权时所见即所得。
--
-- 目标（仓库小程序 11040 下分层）
--   仓库小程序(11040)
--     ├ 蔬菜处理(11041)  毛菜处理录入 9096 / 月台入库 9097 / 蔬菜入库 9098
--     ├ 分拣发货(11042)  物资领用 9106 / 物资退回 9107 / 物资损耗 9108
--     │                  清点确认 9201 / 白条发货 9202 / 蔬菜发货 9203 / 礼盒发货 9204 /
--     │                  门店发货 9205 / 退货录入 9206
--     │                  多库一览 9261 / 盘点明细 9262 / 实盘录入 9263 / 明细修改 9264 / 明细删除 9265
--     └ （仍直挂 11040）白条分割 9082-9084 / 打包工序 9221-9225 / 包材 9247-9248 / 需求调度 9271-9275
--
-- 说明
--   1) 仅调 parent_id（树展示分层），不改 perms / 不改 sys_role_menu 授权（授权由 BOARD-001 管）。
--   2) 两个二级容器 visible='1' 仅授权树可见、不进 admin 侧边栏；menu_type='M' 无 perm；
--      在 11000-11999 小程序权限号段内（11041/11042 紧邻 11040）。补非空 path 避免 /getRouters
--      下发空 path 路由崩 vue-router（同 V202607221412 教训）。
--   3) 刷新角色弹框（实时 DB 查询授权树）即生效，无需 flush redis。

-- 1) 新建仓库 mp 二级容器
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
  (11041, '蔬菜处理', 11040, 1, 'mp-wh-veg',      NULL, '', 1, 0, 'M', '1', '0', '', 'tree', 1, NOW(), 'DJS-PERM-MENU-002 仓库 mp tab 蔬菜处理'),
  (11042, '分拣发货', 11040, 2, 'mp-wh-dispatch', NULL, '', 1, 0, 'M', '1', '0', '', 'tree', 1, NOW(), 'DJS-PERM-MENU-002 仓库 mp tab 分拣发货');

-- 2) re-parent：蔬菜处理 tab 叶子（处理录入 / 月台入库 / 蔬菜入库）
UPDATE sys_menu SET parent_id = 11041 WHERE menu_id IN (9096, 9097, 9098);

-- 3) re-parent：分拣发货 tab 叶子（物资领用 / 发货月台 / 退货 / 库存盘点）
UPDATE sys_menu SET parent_id = 11042
WHERE menu_id IN (9106, 9107, 9108,
                  9201, 9202, 9203, 9204, 9205, 9206,
                  9261, 9262, 9263, 9264, 9265);

-- 验证（按需手动跑）：
-- SELECT c.menu_id, c.menu_name, p.menu_name parent FROM sys_menu c
--   JOIN sys_menu p ON c.parent_id = p.menu_id WHERE p.menu_id IN (11040, 11041, 11042)
--   ORDER BY c.parent_id, c.order_num;
