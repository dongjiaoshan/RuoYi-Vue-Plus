-- ============================================================
-- WMS-FEED-RECORD-001  有机饲喂记录（compute over t_warehouse_feed_log）  菜单 seed
-- ============================================================
-- 仓库-admin 行21：有机饲喂记录（列表，仅查看无操作）
-- 覆盖两类来源（毛菜处理间 veg_handle + 仓库领用 warehouse），按饲喂时间倒序分页。
-- 挂「库存管理」9302 下；menu_id 段：仓库域 9000-9999，库存管理子段取空位 9130。
--   9130 有机饲喂记录（C，path=feedRecord, component=djs-warehouse/feedRecord/index,
--         perms=djs:warehouse:feed:record:*  ← 菜单级通配，对齐 DJS-PERM-MENU-006 范式；
--         只读，不建 add/edit/del 的 F 子节点）
-- role_menu 白名单：role_id NOT IN (1, 101) 业务角色 + 超管 role_id=1 全绑（对齐 WMS-LOSS-OVERVIEW-001 范式）
-- sys_menu / sys_role_menu 为 ruoyi 框架表，无 tenant_id
-- 本迁移不动字典（djs_feed_type 已由 WMS-LOSS-001 灌过），无需跑 _post-init.sh flush redis
-- ============================================================

-- ------------------------------------------------------------
-- 0. feed_date DATE → DATETIME（行21 要求日期精确到时分秒）
-- ------------------------------------------------------------
-- 原 t_warehouse_feed_log.feed_date 为 DATE，写入的 now() 时分秒被截断到日。
-- 行21「有机饲喂记录」要求日期精确到时分秒，改列类型为 DATETIME 让新数据保留时分秒。
-- 历史行补 00:00:00（DATE → DATETIME 安全转换，不丢日期）。
-- 配套：FeedLogMapper.selectDailyStat 的 GROUP BY 已由 feed_date 改 DATE(feed_date)，
--      仍按自然日聚合（DATETIME 下等价），统计语义不变。
ALTER TABLE t_warehouse_feed_log
    MODIFY COLUMN feed_date DATETIME NOT NULL COMMENT '饲喂时间（精确到时分秒）';

-- ------------------------------------------------------------
-- 1. sys_menu seed 9130（仅 1 个 C 菜单，只读无 F 按钮）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9130, '有机饲喂记录', 9302, 13, 'feedRecord', 'djs-warehouse/feedRecord/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:feed:record:*', 'documentation', 1, NOW(), 'WMS-FEED-RECORD-001');

-- ------------------------------------------------------------
-- 2. role_menu 白名单绑定（role_id NOT IN (1, 101) 业务角色 + 超管 role_id=1）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id = 9130;

-- 超级管理员角色 1
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id = 9130;
