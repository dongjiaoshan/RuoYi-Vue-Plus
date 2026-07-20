-- 0613-11 需求确认页菜单：在「需求管理」9040 下挂隐藏 C 路由（从列表「查看需求」下钻进入）。
--
-- 仓库域 menu_id 段 9000-9999；需求管理子段 9040-9054 已占满（4 业态 C + 状态机 F），
-- 需求确认页占同段空号 9055（CLAUDE.md §6.6 需求管理段尾 9055-9059 空闲）。
-- visible='1'（隐藏，不在左侧菜单显示，仅作动态路由注册）；path='confirm' 挂 9040(path=demand)
-- → 完整路由 /djs-warehouse/demand/confirm；component=djs-warehouse/demand/confirm/index；
-- perms 复用 djs:warehouse:demand:list（同入口同角色，不新增权限串）。
-- 改 sys_menu 后跑 script/sql/djs/_post-init.sh flush redis 菜单/字典缓存，重新登录拉新 getRouters。

INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
   menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (9055, '需求确认', 9040, 15, 'confirm', 'djs-warehouse/demand/confirm/index', '', 1, 0,
   'C', '1', '0', 'djs:warehouse:demand:list', '#', 1, NOW(), '0613-11 需求确认页（隐藏，从需求管理列表下钻）');

-- 授权：照需求管理 9040 现有持有角色补授（保持确认页与需求管理列表可见性一致）。
-- 9040 持有者 = 超管 role 1 + 仓库相关业务角色（WMS-DEMAND-001 seed + 后续补授）。INSERT IGNORE 幂等。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 9055 FROM sys_role_menu WHERE menu_id = 9040;
