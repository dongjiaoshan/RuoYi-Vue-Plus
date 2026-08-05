-- ============================================================
-- admin row185 + row187：毛菜间出库（单条内部处理 + 批量出库管理）
--   1. 出库去向字典补「果蔬月台」（原字典只有 ship_dock 发货月台，是另一回事）
--   2. t_warehouse_stock_flow 加 batch_no：同一次批量出库共用，列表按单聚合
--   3. 新菜单「毛菜间出库管理」挂库存管理(9302)、排在库存查询(order=4)之后
-- ============================================================

-- 1. 出库去向字典：果蔬月台（毛菜鲜品库 L0006 出库 → 果蔬月台 → 工人在 mp 月台收货入蔬菜保鲜库）
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type,
                           css_class, list_class, is_default, create_by, create_time, remark)
VALUES (1029140, '1001', 12, '果蔬月台', 'veg_dock', 'djs_stock_out_dest',
        '', 'success', 'N', 1, NOW(), 'ADMIN-R185 毛菜鲜品库出库到果蔬月台');

-- 2. 批量出库单号：同一次提交的多条出库流水共用一个 batch_no，列表按它聚合成「一单」；
--    单条出库（产品出库 / 产品内部处理）为 NULL，不进批量列表。
ALTER TABLE t_warehouse_stock_flow
    ADD COLUMN batch_no VARCHAR(32) NULL COMMENT '批量出库单号（毛菜间批量出库同一次提交共用；单条出库为 NULL）' AFTER flow_no;

CREATE INDEX idx_stock_flow_batch ON t_warehouse_stock_flow (tenant_id, batch_no);

-- 3. 菜单：毛菜间出库管理。先把 order>=5 的既有子菜单整体后移一位，给新菜单腾出 order=5。
UPDATE sys_menu SET order_num = order_num + 1 WHERE parent_id = 9302 AND order_num >= 5;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (9134, '毛菜间出库管理', 9302, 5, 'vegOut', 'djs-warehouse/vegOut/index', '', 1, 0,
        'C', '0', '0', 'djs:warehouse:vegOut:list', 'download', 103, 1, NOW(), 'ADMIN-R187 毛菜间批量出库'),
       (9135, '毛菜间出库查询', 9134, 1, '', NULL, '', 1, 0,
        'F', '0', '0', 'djs:warehouse:vegOut:query', '#', 103, 1, NOW(), 'ADMIN-R187'),
       (9136, '毛菜间出库新增', 9134, 2, '', NULL, '', 1, 0,
        'F', '0', '0', 'djs:warehouse:vegOut:add', '#', 103, 1, NOW(), 'ADMIN-R187');

-- 4. 角色授权（关键：不授权则只有超管看得见该菜单，所有真实业务角色进页面报「没有访问权限」）。
--    授权口径 = 对齐同一「库存管理」区块下的兄弟菜单「库存查询」(9020) 已有的角色集合
--    （系统管理员 / 老板 / 管理人员 / 仓库管理员），毛菜间出库属仓库日常作业，范围一致。
--    幂等 INSERT IGNORE；授权后真实用户需重新登录（token 里的 menuPermission 是登录态快照）。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 9134 AS menu_id UNION ALL SELECT 9135 UNION ALL SELECT 9136) m
WHERE r.role_key IN ('system_admin', 'boss', 'manager', 'warehouse_admin')
  AND r.del_flag = '0';
