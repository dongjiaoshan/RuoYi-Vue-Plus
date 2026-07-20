-- FIX-MED-MODEL-006 药品库/批次菜单从养殖父迁仓库域（ADR-0012 药品归仓库库位统一）
-- 药品归仓库 → admin「药品库 7060 / 药品批次 7070」从养殖父 7000 挂到仓库「仓库配置管理 9303」下。
-- 仅改挂载位置（parent_id），component 不动（前端文件迁移作可选优化，本次不迁）；
-- 按钮权限是 7060/7070 的子菜单，parent 不变随主菜单移动，授权关系（sys_role_menu 按 menu_id）不变。

UPDATE sys_menu SET parent_id = 9303 WHERE menu_id IN (7060, 7070);
