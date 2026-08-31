-- V6-R151 菜单：运营管理 → 农场信息 →「果蔬上市计划」
--
-- 甲方 2026-08-31：农场信息下新增【果蔬上市计划】页 —— 按种植计划一行展示
-- 作物图片 / 作物名称 / 预计产量 / 实际产量 / 上市月份 / 下市月份，支持作物名称模糊 +
-- 上市月份 + 下市月份三项筛选，按上市月份降序，并支持导出（列与列表一致）。
--
-- 不建任何业务表：数据 100% 来自 t_plant_plant_plan + t_plant_plant_details + t_plant_crop_info
-- 的只读聚合（上市月份 = MIN(明细 earliest_harvestdate)、下市月份 = MAX(明细 last_harvestdate)）。
--
-- 父目录 12000（运营管理）/ 12010（农场信息）由 V202609010100__V6-R149-ops-board-menu.sql 建立，
-- 版本号更早，必先于本迁移执行。号段按 R149 划分：12020-12029 = 果蔬上市计划。sys_menu 无 tenant_id 列。
--
-- 前端：component = 'djs-ops/marketPlan/index'（plus-ui 路由由 sys_menu.component 动态装配，无需改 router）。
-- 后端：GET /djs/ops/marketPlan/list、POST /djs/ops/marketPlan/export（org.dromara.djs.plant.market.*）。
--
-- 生效：/getRouters 实时读库，重新登录即生效；sys_menu 不进 redis 字典缓存，无需 flush。
-- 幂等：全部 INSERT IGNORE，可重复执行。

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (12020, '果蔬上市计划', 12010, 1, 'marketPlan', 'djs-ops/marketPlan/index', '',
     1, 0, 'C', '0', '0',
     'djs:ops:marketPlan:list', 'date', 1, NOW(), 'V6-R151 按种植计划聚合的上市/下市月份列表（只读）'),
    (12021, '查询', 12020, 1, '', '', '',
     1, 0, 'F', '0', '0',
     'djs:ops:marketPlan:list', '#', 1, NOW(), 'V6-R151 GET /djs/ops/marketPlan/list —— 与父菜单同串：本页只读无详情端点，不另设 :query'),
    (12022, '导出', 12020, 2, '', '', '',
     1, 0, 'F', '0', '0',
     'djs:ops:marketPlan:export', '#', 1, NOW(), 'V6-R151 POST /djs/ops/marketPlan/export');

-- 角色授权（ADR-0020：子树必须与父目录同授权，否则子菜单成孤儿不显示）
-- ① 派生：凡是已拿到父目录 12010 的角色，同样拿 12020/12021/12022
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, m.menu_id
FROM sys_role_menu rm
CROSS JOIN (SELECT 12020 AS menu_id UNION ALL SELECT 12021 UNION ALL SELECT 12022) m
WHERE rm.menu_id = 12010;

-- ② 显式兜底（与 R149 给 12000/12010 的授权面严格一致：1 超管 / 101 系统管理员 / 102 老板 / 103 管理人员）
--    若 ① 命中 0 行（父目录授权尚未落库），由本段保证页面可见。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 12020), (1, 12021), (1, 12022),
    (101, 12020), (101, 12021), (101, 12022),
    (102, 12020), (102, 12021), (102, 12022),
    (103, 12020), (103, 12021), (103, 12022);

-- 验收 query
--   SELECT menu_id, menu_name, parent_id, order_num, path, component, menu_type, perms
--     FROM sys_menu WHERE menu_id BETWEEN 12020 AND 12029 ORDER BY menu_id;
--   SELECT menu_id, GROUP_CONCAT(role_id ORDER BY role_id) FROM sys_role_menu
--     WHERE menu_id BETWEEN 12020 AND 12029 GROUP BY menu_id;
