-- DJS-PERM-MENU-001：把散落 / 与 admin 交织的 mp(小程序) 权限菜单，归到独立的「小程序权限」树。
--
-- 背景
--   角色授权弹框（修改角色 → 菜单权限树）里，mp 权限节点混乱：16 个直接挂根(parent_id=0)与
--   admin 模块(系统管理/养殖/仓库…)平级交织；其余约 45 个嵌在 visible=1 的 mp-only 容器
--   (农事录入/发货月台/打包工序/盘点录入/需求调度…)下，而这些容器又挂在 admin 域树里 ——
--   导致 admin 与 mp 权限混在一起，难配。
--
-- 目标（顶层 admin / mp 分离；mp 树内按板块平铺）
--   【admin】系统管理 / 系统监控 / 系统工具 / 通用主数据 / 养殖 / 种植 / 仓库 / 门店管理（只剩 admin）
--   【mp】 小程序权限 (11000，visible=1 不进侧边栏)
--      ├ 通用小程序(11010)  OSS 凭证 + 跨板块共享 picker（门店/库位/产品/猪只）
--      ├ 养殖小程序(11020)  分娩/栋舍/栏位 picker · 生长记录 · 饲料 · 仔猪耳标 · 阉割 · 出栏
--      ├ 种植小程序(11030)  采摘任务 · 采收 · 农事 · 班组
--      ├ 仓库小程序(11040)  白条分割 · 毛菜 · 物资 · 发货 · 打包 · 包材 · 盘点 · 需求调度
--      └ 门店小程序(11050)  V1 暂空，预留
--   与 DJS-PERM-BOARD-001 板块化授权一一对应：勾板块节点（父子联动）= 授该板块全部 mp 权限。
--
-- 说明
--   1) 仅移动菜单 parent_id（树展示），不改 perms / 不改 sys_role_menu 授权（授权由 BOARD-001 管）。
--   2) 按消费板块归类：饲料(breed:feed) 归养殖；跨板块 picker(门店/库位/产品/猪只) 归通用。
--   3) 混合节点（分割记录 9076 / 毛菜处理 9090 / 包材领用 9100，C 型自带 admin perm）保留在 admin 树，
--      只迁出其 mp 子节点。
--   4) 搬空后的纯 mp 容器(8090/8500/9200/9220/9246/9260/9270，visible=1 无 perm) 删除；其父
--      (农事管理/种植/生产管理/库存管理/发货管理) 均仍有其他 admin 子节点，不级联空。
--   5) menu 改动角色弹框树为实时 DB 查询，刷新页面即生效；无需 flush redis。

-- 1) 新建 mp 权限树容器（visible='1' 仅授权树可见、不进 admin 侧边栏；order_num 大值排在 admin 模块之后）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
  (11000, '小程序权限', 0,     900, '', NULL, '', 1, 0, 'M', '1', '0', '', 'phone',     1, NOW(), 'DJS-PERM-MENU-001 mp 权限树顶级'),
  (11010, '通用小程序', 11000, 1,   '', NULL, '', 1, 0, 'M', '1', '0', '', 'component', 1, NOW(), '跨板块共享 picker + OSS'),
  (11020, '养殖小程序', 11000, 2,   '', NULL, '', 1, 0, 'M', '1', '0', '', 'tree',      1, NOW(), '养殖 mp 权限'),
  (11030, '种植小程序', 11000, 3,   '', NULL, '', 1, 0, 'M', '1', '0', '', 'tree',      1, NOW(), '种植 mp 权限'),
  (11040, '仓库小程序', 11000, 4,   '', NULL, '', 1, 0, 'M', '1', '0', '', 'tree',      1, NOW(), '仓库 mp 权限'),
  (11050, '门店小程序', 11000, 5,   '', NULL, '', 1, 0, 'M', '1', '0', '', 'tree',      1, NOW(), '门店 mp 权限（V1 暂空，预留）');

-- 2) re-parent：把 mp 权限叶子按板块归到新容器（显式 menu_id，精确不误伤 admin 节点）

-- 通用小程序：OSS + 跨板块共享 picker（门店/库位/产品/猪只）
UPDATE sys_menu SET parent_id = 11010
WHERE menu_id IN (5053, 5350, 5351, 5352, 7360);

-- 养殖小程序：分娩/栋舍/栏位 picker · 生长记录 · 饲料 · 仔猪耳标 · 阉割 · 出栏
UPDATE sys_menu SET parent_id = 11020
WHERE menu_id IN (5353, 5354, 5355, 5356, 5357, 7150, 7151, 7153, 7361, 7362, 7363, 7364, 7365);

-- 种植小程序：采摘任务 · 采收 · 农事 · 班组
UPDATE sys_menu SET parent_id = 11030
WHERE menu_id IN (8088, 8089, 8091, 8092, 8093, 8094, 8095, 8096, 8097, 8501, 8502);

-- 仓库小程序：白条分割 · 毛菜 · 物资 · 发货 · 打包 · 包材 · 盘点 · 需求调度
UPDATE sys_menu SET parent_id = 11040
WHERE menu_id IN (9082, 9083, 9084, 9096, 9097, 9098, 9106, 9107, 9108,
                  9201, 9202, 9203, 9204, 9205, 9206,
                  9221, 9222, 9223, 9224, 9225, 9247, 9248,
                  9261, 9262, 9263, 9264, 9265,
                  9271, 9272, 9273, 9274, 9275);

-- 3) 删除搬空后的纯 mp 容器（子节点已全部迁出；混合节点 9076/9090/9100 保留）
DELETE FROM sys_role_menu WHERE menu_id IN (8090, 8500, 9200, 9220, 9246, 9260, 9270);
DELETE FROM sys_menu      WHERE menu_id IN (8090, 8500, 9200, 9220, 9246, 9260, 9270);

-- 验证（按需手动跑）：mp 权限是否全部归到 11000 树下、根上不再有 mp floater
-- SELECT p.menu_name parent, COUNT(*) cnt FROM sys_menu c JOIN sys_menu p ON c.parent_id=p.menu_id
-- WHERE c.menu_name LIKE 'mp %' OR c.perms LIKE 'djs:applet:%' OR c.menu_id IN (7150,7151,7153) GROUP BY p.menu_name;
-- SELECT menu_id, menu_name FROM sys_menu WHERE parent_id=0 ORDER BY order_num;  -- 应只剩 admin 模块 + 小程序权限
