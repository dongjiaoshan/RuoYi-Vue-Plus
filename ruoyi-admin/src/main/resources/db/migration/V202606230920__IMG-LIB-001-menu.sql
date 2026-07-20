-- ============================================================
-- IMG-LIB-001 公共图库 + 分类默认图 菜单
--   父菜单 5000 (通用主数据, djs-common) 已在 SYS-AUTH-001 占位
--   段：5500-5519 公共图库 / 5520-5529 分类默认图（系统底座 5000-5999 内空闲段）
--   perms 前缀：djs:common:image:* / djs:common:defaultImage:*
-- ============================================================

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 公共图库 列表页（C）
  (5500, '公共图库', 5000, 50, 'image', 'djs-common/image/index', '', 1, 0, 'C', '0', '0',
   'djs:common:image:list', 'picture', 1, NOW(), 'IMG-LIB-001'),
  (5501, '图库查询',     5500, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:image:list',    '#', 1, NOW(), 'IMG-LIB-001'),
  (5502, '图库新增',     5500, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:image:add',     '#', 1, NOW(), 'IMG-LIB-001'),
  (5503, '图库修改',     5500, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:image:edit',    '#', 1, NOW(), 'IMG-LIB-001'),
  (5504, '图库删除',     5500, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:image:remove',  '#', 1, NOW(), 'IMG-LIB-001'),
  (5505, '图库导出',     5500, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:image:export',  '#', 1, NOW(), 'IMG-LIB-001'),
  (5506, '批量重新匹配', 5500, 6, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:image:rematch', '#', 1, NOW(), 'IMG-LIB-001'),
  -- 分类默认图 配置页（C）
  (5520, '分类默认图', 5000, 51, 'defaultImage', 'djs-common/defaultImage/index', '', 1, 0, 'C', '0', '0',
   'djs:common:defaultImage:list', 'picture-rounded', 1, NOW(), 'IMG-LIB-001'),
  (5521, '默认图查询', 5520, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:defaultImage:list', '#', 1, NOW(), 'IMG-LIB-001'),
  (5522, '默认图配置', 5520, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:defaultImage:edit', '#', 1, NOW(), 'IMG-LIB-001');

-- 角色授权：superadmin(1) 显式 + boss/manager/system_admin 按 djs:% 通配（与 DJS-FIX-ROLE-PERMS-002 一致）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id BETWEEN 5500 AND 5529;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r CROSS JOIN sys_menu m
WHERE m.menu_id BETWEEN 5500 AND 5529
  AND r.del_flag = '0'
  AND r.role_key IN ('boss', 'manager', 'system_admin');
