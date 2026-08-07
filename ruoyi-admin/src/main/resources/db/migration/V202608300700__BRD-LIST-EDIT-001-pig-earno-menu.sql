-- BRD-LIST-EDIT-001：admin 猪只主表新增「修改耳号」按钮权限。
--   小程序录入耳号偶尔手误录错序号（如 01-01-2-251031-031 想改成 030），此前只能找开发手工
--   改库，本 ticket 给 母猪/公猪/仔猪/育肥猪 4 个列表页各加一个「修改耳号」按钮，共用同一后端
--   权限串 djs:breed:pig:edit（PigController.updateEarNo，PUT /djs/breed/pig/{id}/ear-no）。
--   按钮号段：各列表组（7310-7319/7320-7329/7330-7339/7340-7349）此前只用到前 3 位，用组内第 4 位。

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7313, '母猪改耳号',   7300, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pig:edit', '#', 1, NOW(), 'BRD-LIST-EDIT-001'),
  (7324, '公猪改耳号',   7320, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pig:edit', '#', 1, NOW(), 'BRD-LIST-EDIT-001'),
  (7334, '仔猪改耳号',   7330, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pig:edit', '#', 1, NOW(), 'BRD-LIST-EDIT-001'),
  (7344, '育肥猪改耳号', 7340, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pig:edit', '#', 1, NOW(), 'BRD-LIST-EDIT-001');

-- ------------------------------------------------------------
-- 角色授权：superadmin(1) + 养殖管理员(breed_admin) —— 数据纠错是管理层操作，
--   不下放给养殖工人(breed_worker)。role_id=1 按项目惯例显式补（"新建菜单必须同时授权"）。
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 7313 AS menu_id UNION SELECT 7324 UNION SELECT 7334 UNION SELECT 7344) m
WHERE r.del_flag = '0' AND (r.role_id = 1 OR r.role_key = 'breed_admin');
