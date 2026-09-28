-- V6 row263: "在生产管理菜单下面新增一级菜单：出入库管理".
-- "下面" is the next sibling position, not a child of production management.
-- Keep the two workstations and all existing role grants under menu 9310.
-- Append this correction; V202609271010 has already been applied.
SET @r263_parent = (SELECT parent_id FROM sys_menu WHERE menu_id = 9300);
SET @r263_order = (SELECT order_num + 1 FROM sys_menu WHERE menu_id = 9300);
SET @r263_move = EXISTS (
    SELECT 1 FROM sys_menu WHERE menu_id = 9310 AND parent_id <> @r263_parent
);

-- Make one slot immediately after production; a repeat does not shift siblings again.
UPDATE sys_menu
SET order_num = order_num + 1
WHERE @r263_move = 1
  AND parent_id = @r263_parent
  AND menu_id <> 9310
  AND order_num >= @r263_order;

UPDATE sys_menu
SET parent_id = @r263_parent,
    order_num = @r263_order,
    update_by = 1,
    update_time = NOW(),
    remark = 'V6-R263：仓库一级目录，与生产管理同级并紧随其后'
WHERE menu_id = 9310;
