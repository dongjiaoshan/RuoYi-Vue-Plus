-- V6-R150 菜单：运营管理 → 农场信息 →「育肥猪信息」
--
-- 甲方 2026-08-31：农场信息下新增【育肥猪信息】页 —— 顶部育肥猪 / 仔猪两个 tab（tab 名后带当前存栏头数），
-- 每个 tab 内上方日龄分布柱状图（口径与小程序「猪只库存信息」页完全一致）、下方栋舍 × 日龄段矩阵列表，
-- 点柱状图某一日龄区间时列表只保留该区间列。
--
-- 父目录 12000（运营管理）/ 12010（农场信息）由 V202609010100__V6-R149-ops-board-menu.sql 建立。
-- 号段按 R149 划分：12030-12039 = 育肥猪信息。sys_menu 无 tenant_id 列。
--
-- 前端：component = 'djs-ops/fattenInfo/index'（plus-ui 路由由 sys_menu.component 动态装配，无需改 router）。
-- 后端：GET /djs/breed/inventory/{age-dist,barn-matrix}，权限串 djs:ops:fattenInfo:query。
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
    (12030, '育肥猪信息', 12010, 2, 'fattenInfo', 'djs-ops/fattenInfo/index', '',
     1, 0, 'C', '0', '0',
     'djs:ops:fattenInfo:list', 'chart', 1, NOW(), 'V6-R150 育肥猪/仔猪日龄分布 + 栋舍矩阵'),
    (12031, '查询', 12030, 1, '', '', '',
     1, 0, 'F', '0', '0',
     'djs:ops:fattenInfo:query', '#', 1, NOW(), 'V6-R150');

-- 角色授权（ADR-0020：子树必须与父目录同授权，否则子菜单成孤儿不显示）
-- ① 派生：凡是已拿到父目录 12010 的角色，同样拿 12030/12031
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, m.menu_id
FROM sys_role_menu rm
CROSS JOIN (SELECT 12030 AS menu_id UNION ALL SELECT 12031) m
WHERE rm.menu_id = 12010;

-- ② 显式兜底（与 R149 给 12000/12010 的授权面严格一致：1 超管 / 101 系统管理员 / 102 老板 / 103 管理人员）
--    若两个迁移执行顺序反转导致 ① 命中 0 行，由本段保证页面可见。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 12030), (1, 12031),
    (101, 12030), (101, 12031),
    (102, 12030), (102, 12031),
    (103, 12030), (103, 12031);

-- 验收 query
--   SELECT menu_id, menu_name, parent_id, order_num, path, component, menu_type, perms
--     FROM sys_menu WHERE menu_id IN (12030, 12031) ORDER BY menu_id;
--   SELECT menu_id, GROUP_CONCAT(role_id ORDER BY role_id) FROM sys_role_menu
--     WHERE menu_id IN (12030, 12031) GROUP BY menu_id;
