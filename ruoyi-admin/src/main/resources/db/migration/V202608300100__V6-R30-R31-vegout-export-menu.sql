-- ============================================================
-- V6 row30 + row31：毛菜间出库管理两处导出
--   row31 列表页工具条「导出」（按当前筛选导出出库单列表）
--   row30 「出库明细」弹框内「导出」（导出该单的产品明细）
-- 两处共用一个权限点 djs:warehouse:vegOut:export，故只建一条 F 菜单。
-- ============================================================

-- 1. 按钮权限菜单：挂「毛菜间出库管理」(9134) 下，排在 查询(1) / 新增(2) 之后。
--    9134 的 perms 是精确串 djs:warehouse:vegOut:list（不是通配 *），
--    不单独发这条 export 权限点，前端按钮 v-hasPermi 不显示、后端 @SaCheckPermission 直接 403。
DELETE FROM sys_menu WHERE menu_id = 9137;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (9137, '毛菜间出库导出', 9134, 3, '', NULL, '', 1, 0,
        'F', '0', '0', 'djs:warehouse:vegOut:export', '#', 103, 1, NOW(), 'V6-R30-R31 列表导出 + 明细导出');

-- 2. 角色授权：授给「已经能看毛菜间出库管理列表(9134)」的全部角色 —— 今天是
--    系统管理员(101) / 老板(102) / 管理人员(103) / 仓库管理员(106) 四个。
--    从 9134 的既有授权派生而不是写死 role_id：导出与列表天然同权（能看列表就能导出这份列表），
--    将来 9134 的角色集合调整时这条不会变成一份对不上的副本。
--    ⚠️ 这条授权的作用是**前端按钮显隐**（v-hasPermi）——不授权则该角色看不到「导出」按钮。
--    它不是安全边界：后端权限按 Kevin 2026-08-06 的口径不做硬限制（实测非授权账号直接打接口也能通），
--    页面级的可见/不可见由前端控制。别把这条当访问控制写进任何依赖它的逻辑。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, 9137
FROM sys_role_menu rm
WHERE rm.menu_id = 9134;
