-- ============================================================
-- PLT-WORK-002 农事记录列表（admin PC 单页 + 5 Tab 只读查询）
--
-- 复用 PLT-WORK-001 表 t_plant_farm_records / 字典 djs_farm_work_type / be list 端点
-- （GET /djs/plant/farm/list，权限 djs:plant:farm:list；导出权限 djs:plant:farm:export 已 seed）。
-- 本 SQL 只补 admin 可见菜单：
--   8098 农事记录列表 C 菜单（挂 8090 农事录入父下，component → djs-plant/work/records/index）
--   8099 导出按钮权限 F（复用已有 perms djs:plant:farm:export）
--
-- menu_id 段：种植 8000-8999；农事录入子段 8090-8099（PLT-WORK-001 占 8090-8097，本 ticket 占 8098-8099）。
-- 角色白名单：role_id NOT IN (1, 101) AND del_flag='0'（与 PLT-WORK-001 同范式）。
-- ============================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query_param,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    -- 8098 农事记录列表（admin 可见 C 菜单；list 权限复用 djs:plant:farm:list）
    (8098, '农事记录', 8090, 8, 'records', 'djs-plant/work/records/index', '',
     1, 0, 'C', '0', '0', 'djs:plant:farm:list', 'documentation', 1, NOW(),
     'PLT-WORK-002 admin PC 单页 + 5 Tab 只读查询；复用 t_plant_farm_records'),
    -- 8099 导出按钮权限 F（复用 PLT-WORK-001 已 seed perms djs:plant:farm:export）
    (8099, '农事记录导出', 8098, 1, '', '', '',
     1, 0, 'F', '1', '0', 'djs:plant:farm:export', '#', 1, NOW(),
     'PLT-WORK-002 列表页导出按钮权限');

-- 角色白名单：role_id NOT IN (1, 101) AND del_flag='0'
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id BETWEEN 8098 AND 8099;
