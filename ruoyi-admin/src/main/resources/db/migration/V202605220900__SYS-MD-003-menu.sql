-- ============================================================
-- SYS-MD-003 供应商管理菜单 + 按钮权限
--   父菜单 5003 (供应商管理) 由本 ticket seed（SYS-AUTH-001 未占位）
--   字典 djs_supplier_type 6 类已在 D1 SYS-INIT-002 灌
--   role 101/102/103 通过 SYS-AUTH-001 BETWEEN 5000-10999 兜底，本文件不写 sys_role_menu
-- ============================================================

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (5003, '供应商管理', 5000, 3, 'supplier', 'djs-common/supplier/index', '', 1, 0, 'C', '0', '0',
   'djs:common:supplier:list',     'peoples', 1, NOW(), 'SYS-MD-003'),
  (5030, '供应商查询', 5003, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:supplier:list',     '#', 1, NOW(), 'SYS-MD-003'),
  (5031, '供应商新增', 5003, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:supplier:add',      '#', 1, NOW(), 'SYS-MD-003'),
  (5032, '供应商修改', 5003, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:supplier:edit',     '#', 1, NOW(), 'SYS-MD-003'),
  (5033, '供应商删除', 5003, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:supplier:remove',   '#', 1, NOW(), 'SYS-MD-003'),
  (5034, '供应商导出', 5003, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:supplier:export',   '#', 1, NOW(), 'SYS-MD-003');
