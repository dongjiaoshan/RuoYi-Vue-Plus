-- PLT-PICK-001 mp 工人采收录入权限 seed
-- 段位 8500-8599 = 种植 mp 工人操作段（CLAUDE.md §6 种植 8000-8999 二级分段）。
-- D10 PLT-PLAN-002 已 seed pick:list(8088) / pick:detail(8089) 于父 8081，本 ticket 不重复，
-- 仅新增 submit / summary 2 个 mp 权限串，挂新父 8500（隐藏 M 容器，纯权限挂点）。

INSERT INTO sys_menu
(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    -- 8500 父：种植采收操作（隐藏权限容器，挂种植根 8000 下）
    (8500, '种植采收操作', 8000, 50, 'pickWork', '', '', 1, 0, 'M', '1', '0', '',                                'date', 1, NOW(), 'PLT-PICK-001 mp 工人采收权限容器（隐藏）'),
    -- 8501 mp 采收提交
    (8501, 'mp 采收提交',  8500, 1,  '',         '', '', 1, 0, 'F', '0', '0', 'djs:applet:plant:pick:submit',    '#',    1, NOW(), 'PLT-PICK-001 工人录采收重量 / 完成采收'),
    -- 8502 mp 采收概览
    (8502, 'mp 采收概览',  8500, 2,  '',         '', '', 1, 0, 'F', '0', '0', 'djs:applet:plant:pick:summary',   '#',    1, NOW(), 'PLT-PICK-001 采收 KPI 概览（按作物聚合）');

-- role_menu 白名单（不绑 super_admin=1 / common=101，业务岗位才绑）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id BETWEEN 8500 AND 8502;
