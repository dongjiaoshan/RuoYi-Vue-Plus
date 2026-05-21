-- ============================================================
-- SYS-MD-002 门店管理菜单 + 按钮权限
--   一级父菜单 5000 (通用主数据) 已在 SYS-AUTH-001 占位
--   本 ticket 新增二级父菜单 5002 (门店管理) 及 5 个按钮权限 5020-5024
--   role 101/102/103 通过 SYS-AUTH-001 BETWEEN 5000-10999 兜底，本文件不写 sys_role_menu
-- ============================================================

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 二级目录：门店管理（挂在通用主数据 5000 下）
  (5002, '门店管理', 5000, 2, 'store', 'djs-common/store/index', '',
   1, 0, 'C', '0', '0',
   'djs:common:store:list', 'shop', 1, NOW(), 'SYS-MD-002'),

  -- 三级按钮权限
  (5020, '门店查询', 5002, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:store:list',    '#', 1, NOW(), 'SYS-MD-002'),
  (5021, '门店新增', 5002, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:store:add',     '#', 1, NOW(), 'SYS-MD-002'),
  (5022, '门店修改', 5002, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:store:edit',    '#', 1, NOW(), 'SYS-MD-002'),
  (5023, '门店删除', 5002, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:store:remove',  '#', 1, NOW(), 'SYS-MD-002'),
  (5024, '门店导出', 5002, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:store:export',  '#', 1, NOW(), 'SYS-MD-002');
