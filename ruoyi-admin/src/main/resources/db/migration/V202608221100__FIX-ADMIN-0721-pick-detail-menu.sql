-- ============================================================
-- FIX-ADMIN-0721  采摘明细：admin 菜单 seed（飞书admin row8）
-- ============================================================
-- 采摘管理（分组 M 8004）下新增「采摘明细」只读列表页（C）：读 t_warehouse_handle_record
--   record_type=1 采收过磅流水（与仓库统计权威口径同源），列 = 采摘日期/作物名称/地块编号/
--   采摘量/采摘班组；筛选 采摘日期范围 + 作物名模糊 + 班组；支持导出。
-- menu_id 段：种植域 8000-8999，采摘管理组 8004 下现占 采摘活动(8084 order 1)/采摘计划(8082 order 2)；
--   复用空号 8085（原游客采摘活动新增 F 按钮，sys_menu + sys_role_menu 已删净），order_num=3。
-- 后端端点复用毛菜处理 controller（/djs/warehouse/vegHandle/pickDetail/*，
--   perms djs:warehouse:vegHandle:list|export）；菜单 perms 走种植采摘段通配。
-- sys_menu / sys_role_menu 为 ruoyi 框架表，无 tenant_id。跑完 flush redis（_post-init.sh 或重启 admin）。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. sys_menu seed 8085：采摘明细（C，挂采摘管理组 8004）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (8085, '采摘明细', 8004, 3, 'detail', 'djs-plant/pick/detail/index', '',
     1, 0, 'C', '0', '0',
     'djs:plant:pick:detail:*', 'list', 1, NOW(), 'FIX-ADMIN-0721');

-- ------------------------------------------------------------
-- 2. role_menu 白名单绑定（业务角色 role_id NOT IN (1) 全绑 + 超管 role_id=1 全绑）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1)
  AND r.del_flag = '0'
  AND m.menu_id = 8085;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id = 8085;
