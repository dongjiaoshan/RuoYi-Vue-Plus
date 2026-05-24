-- ============================================================
-- SYS-MD-001 人员管理菜单按钮权限
--   父菜单 5001 (人员管理) 已在 SYS-AUTH-001 占位 (path='person')
--   状态字典走 D1 SYS-INIT-002 已 seed 的 djs_user_status（0 在职/1 离职/2 试用）
--   role 101/102/103 通过 SYS-AUTH-001 BETWEEN 5000-10999 兜底，本文件不写 sys_role_menu
-- ============================================================

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (5010, '人员查询', 5001, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:person:list',    '#', 1, NOW(), 'SYS-MD-001'),
  (5011, '人员新增', 5001, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:person:add',     '#', 1, NOW(), 'SYS-MD-001'),
  (5012, '人员修改', 5001, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:person:edit',    '#', 1, NOW(), 'SYS-MD-001'),
  (5013, '人员删除', 5001, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:person:remove',  '#', 1, NOW(), 'SYS-MD-001'),
  (5014, '人员导出', 5001, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:person:export',  '#', 1, NOW(), 'SYS-MD-001');
