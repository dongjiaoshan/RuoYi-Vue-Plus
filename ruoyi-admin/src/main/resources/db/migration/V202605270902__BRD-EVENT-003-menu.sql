-- ============================================================
-- BRD-EVENT-003 仔猪批量耳标（菜单 + 按钮权限）
--   父菜单 7000 (养殖) 在 SYS-AUTH-001 占位；本文件分配 7110-7119 段
--   参 .claude/CLAUDE.md §6 第 6 条：7100-7199 留给 BRD-EVENT-001~005 业务事件
--     7110          二级目录：仔猪耳标（小程序入口 + 后续 admin 列表）
--     7111-7113     三级按钮：查询 / 批量贴标
--   权限串 djs:breed:event:eartag(:query) 与 PigEarTagController @SaCheckPermission 一致
--   注：本菜单主要服务小程序写入；admin 端 V1 不提供独立列表页（详情通过 BRD-LIST-001 接入）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 菜单：仔猪耳标（挂养殖目录 7000 下，order_num=60 排在猪只主表之后）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 二级目录：仔猪耳标
  (7110, '仔猪耳标', 7000, 60, 'eartag', '', '',
   1, 0, 'M', '0', '0',
   '', 'edit', 1, NOW(), 'BRD-EVENT-003'),

  -- 三级按钮权限
  (7111, '耳标查询', 7110, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:eartag:query', '#', 1, NOW(), 'BRD-EVENT-003'),
  (7112, '批量贴标', 7110, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:eartag', '#', 1, NOW(), 'BRD-EVENT-003');

-- ------------------------------------------------------------
-- 角色 → 菜单映射
--   query (7111)：所有养殖相关角色
--   eartag write (7112)：boss / manager / pig_keeper（执行人）；butcher 不需要
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 7110 AS menu_id UNION SELECT 7111) m
WHERE r.role_key IN ('boss', 'manager', 'pig_keeper', 'butcher');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 7112
FROM sys_role r
WHERE r.role_key IN ('boss', 'manager', 'pig_keeper');
