-- ============================================================
-- A5 仓库生产管理 admin 打包/分割录入菜单
-- ------------------------------------------------------------
-- 背景：Kevin 口径「仓库生产管理 admin 可写入打包录入」（admin/mp 都能写；只有养殖 admin 只读）。
--   原 production/index.vue 仅只读列表，是缺口。本 ticket 在「仓库 9000 / 生产管理 9300」下挂
--   1 个「打包录入」目录父 + 5 个录入页 C 菜单，对应原型 5 张录入页：
--     肉品打包 / 其他产品打包 / 果蔬打包 / 白条分割管理 / 分割白条领用
--   后端复用 mp 端 ServiceImpl 业务（WarehousePackEntryController /djs/warehouse/packEntry），
--   admin 端权限串 djs:warehouse:packEntry:{dry,gift,veg,cut,pickup}。
--
-- menu_id 分段（仓库 9000-9999；生产管理子段 9230-9248）：
--   9230/9231/9235 = 生产记录（已占，WMS-PACK-001）
--   9240-9248      = 入库/出库记录 + 包材库（已占）
--   本 ticket 占用 9230-9248 内空闲：9232（父）/ 9233 / 9234 / 9236 / 9237 / 9238 / 9239（gift F 子）
-- ============================================================

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 打包录入目录父（挂于 生产管理 9300，order 在 生产记录 之前）
    (9232, '打包录入', 9300, 6, 'packEntry', NULL, '',
     1, 0, 'M', '0', '0', '', 'edit', 1, NOW(), 'A5 admin 打包/分割录入'),

    -- 肉品打包（dry 口）
    (9233, '肉品打包', 9232, 1, 'packEntry/meat', 'djs-warehouse/production/packEntry/meat/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:packEntry:dry', 'goods', 1, NOW(), 'A5 admin 肉品打包录入'),

    -- 其他产品打包（普通产品=dry / 礼盒=gift，C 节点带 dry perm，礼盒 perm 走 9239 F 子）
    (9234, '其他产品打包', 9232, 2, 'packEntry/other', 'djs-warehouse/production/packEntry/other/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:packEntry:dry', 'shopping', 1, NOW(), 'A5 admin 其他产品打包录入'),
    (9239, '礼盒打包权限', 9234, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:packEntry:gift', '#', 1, NOW(), 'A5 admin 礼盒打包按钮权限'),

    -- 果蔬打包（veg 口）
    (9236, '果蔬打包', 9232, 3, 'packEntry/veg', 'djs-warehouse/production/packEntry/veg/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:packEntry:veg', 'tree-table', 1, NOW(), 'A5 admin 果蔬打包录入'),

    -- 白条分割管理（cutOut + cutDone，cut perm）
    (9237, '白条分割管理', 9232, 4, 'packEntry/cut', 'djs-warehouse/production/packEntry/cut/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:packEntry:cut', 'cascader', 1, NOW(), 'A5 admin 白条分割录入'),

    -- 分割白条领用（pickup + whiteBarOut，pickup perm）
    (9238, '分割白条领用', 9232, 5, 'packEntry/pickup', 'djs-warehouse/production/packEntry/pickup/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:packEntry:pickup', 'guide', 1, NOW(), 'A5 admin 分割白条领用录入');

-- ------------------------------------------------------------
-- role_menu 白名单绑定（同 WMS-PACK-001 范式：保留 superadmin/system_admin 通配）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id IN (9232, 9233, 9234, 9239, 9236, 9237, 9238);
