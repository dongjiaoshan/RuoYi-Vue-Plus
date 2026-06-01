-- ============================================================
-- PLT-WORK-003 灾害记录（admin PC 独立子页 + 列表 + 详情，只查）
--
-- 复用 PLT-WORK-001 表 t_plant_farm_records（灾害子集 farm_type='disaster'）+ 字典
-- djs_disaster_type / djs_plot_status / djs_yes_no（D10 已 seed，本 SQL 不新建字典）。
-- 录入入口在 mp PLT-WORK-001 disaster 子页；admin 端不暴露 add/edit/remove。
--
-- 本 SQL 只补 admin 灾害独立子页菜单（与农事录入 8090 父平级，挂种植根 8000 下）：
--   8100 灾害记录 C 菜单（component → djs-plant/work/disaster/index，list 权限 djs:plant:farm:disaster:list）
--   8101 详情查看 F 按钮权限（djs:plant:farm:disaster:list 复用，drawer 详情）
--   8102 导出 F 按钮权限（djs:plant:farm:disaster:export）
--
-- menu_id 段：种植 8000-8999；本 ticket 占 8100-8102（避开 PLT-WORK-001 8090-8097 + PLT-WORK-002 8098-8099）。
-- 角色白名单：role_id NOT IN (1, 101) AND del_flag='0'（与 PLT-WORK-001/002 同范式）。
-- ============================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query_param,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    -- 8100 灾害记录列表（admin 可见 C 菜单；独立子页，非套 5 Tab 模板）
    (8100, '灾害记录', 8000, 10, 'work/disaster', 'djs-plant/work/disaster/index', '',
     1, 0, 'C', '0', '0', 'djs:plant:farm:disaster:list', 'warning', 1, NOW(),
     'PLT-WORK-003 admin 独立灾害子页 + 列表 + 详情（PC 只查）；复用 t_plant_farm_records 灾害子集'),
    -- 8101 详情查看按钮权限 F（drawer 凭证图回显，复用 list 权限）
    (8101, '灾害详情', 8100, 1, '', '', '',
     1, 0, 'F', '1', '0', 'djs:plant:farm:disaster:list', '#', 1, NOW(),
     'PLT-WORK-003 灾害详情 drawer 查看'),
    -- 8102 导出按钮权限 F
    (8102, '灾害记录导出', 8100, 2, '', '', '',
     1, 0, 'F', '1', '0', 'djs:plant:farm:disaster:export', '#', 1, NOW(),
     'PLT-WORK-003 灾害列表导出按钮权限');

-- 角色白名单：role_id NOT IN (1, 101) AND del_flag='0'
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id BETWEEN 8100 AND 8102;
