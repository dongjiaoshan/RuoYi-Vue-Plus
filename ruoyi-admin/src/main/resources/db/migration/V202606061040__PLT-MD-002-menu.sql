-- ============================================================
-- PLT-MD-002 班组管理菜单 (D8)
--
-- menu_id 段：8030-8034（doc/02 v1.2 §6 强约束 / ADR-0006 菜单分段）
--   8030 C 班组管理   path=team   component=djs-plant/team/index
--   8031 F 班组新增   perms=djs:plant:team:add
--   8032 F 班组修改   perms=djs:plant:team:edit
--   8033 F 班组删除   perms=djs:plant:team:remove
--   8034 F 成员管理   perms=djs:plant:team:member
--
-- 父菜单 8000 由 SYS-AUTH-001 (D4) seed。
-- 权限分配：super-admin (role_id=1) 自动拥有所有权限（ruoyi 兜底）；
--           V1 不新建 djs_plant_admin 业务角色 (D7 closing 决策固化)。
-- ============================================================

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (8030, '班组管理', 8000, 30, 'team', 'djs-plant/team/index', NULL, 1, 0, 'C', '0', '0', 'djs:plant:team:list',   'people',  1, NOW(), 'PLT-MD-002'),
  (8031, '班组新增', 8030, 1,  '',    NULL,                    NULL, 1, 0, 'F', '0', '0', 'djs:plant:team:add',    NULL,      1, NOW(), 'PLT-MD-002'),
  (8032, '班组修改', 8030, 2,  '',    NULL,                    NULL, 1, 0, 'F', '0', '0', 'djs:plant:team:edit',   NULL,      1, NOW(), 'PLT-MD-002'),
  (8033, '班组删除', 8030, 3,  '',    NULL,                    NULL, 1, 0, 'F', '0', '0', 'djs:plant:team:remove', NULL,      1, NOW(), 'PLT-MD-002'),
  (8034, '成员管理', 8030, 4,  '',    NULL,                    NULL, 1, 0, 'F', '0', '0', 'djs:plant:team:member', NULL,      1, NOW(), 'PLT-MD-002');

-- 显式分配给 super-admin (role_id=1)；ruoyi role 1 默认有"所有权限"语义但 sys_role_menu 中需要存在行
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (1, 8030), (1, 8031), (1, 8032), (1, 8033), (1, 8034);
