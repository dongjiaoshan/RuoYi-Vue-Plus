-- DJS-PERM-MENU-007：养殖小程序 tab 与 admin 解耦——tab 可见性改由专用「导航权限」控制。
--
-- 问题（Kevin 2026-06-25 自测）
--   admin 角色配置里取消「养殖小程序 → 养殖管理」，但 mp 里该 tab 仍显示。
--   根因：养殖 mp 三 tab 的 gate 串与 admin 页面**同串**（共用 controller / 权限族）：
--     养殖 → djs:breed:event:*（admin 引种登记 7100 / 生长记录 7140 / 耳标 7110）
--     疫苗药品 → djs:breed:med*（admin 药品库 7060 / 批次 7070 / 治疗 7085 / 领用 7076）
--     养殖管理 → djs:breed:dashboard:*（admin 养殖看板 7400）
--   角色只要有对应 admin 页面就拿到该串 → mp tab 误显，取消 mp tab 勾选也压不住。
--   （种植/仓库 mp tab 走 djs:applet:* 专用串，admin 不授，已核实 0 耦合，本迁移不动它们。）
--
-- 解法
--   每个养殖 tab 配一条**专用导航权限** djs:applet:breed:nav:{home,med,dashboard}——只由 mp tab 节点授、
--   admin 永不授。mp 底栏只按导航权限 gate（见 config.ts breedTabbarList）。取消 mp tab 勾选 = 失去导航权限
--   = tab 隐藏，与 admin 页面授权彻底解耦。原端点通配串仍挂 tab 节点本体（M 容器带 perms，
--   selectMenuPermsByUserId 不按 menu_type 过滤，照样下发），父子联动随父勾选级联，页面数据端点不 403。

-- 1) 疫苗药品(11024)/养殖管理(11025)：F→M 容器（保留各自端点通配 perms），补非空 path 防空 path 崩 router
UPDATE sys_menu SET menu_type='M', path='mp-breed-med',  component=NULL WHERE menu_id=11024;  -- 保留 perms=djs:breed:med*
UPDATE sys_menu SET menu_type='M', path='mp-breed-dash', component=NULL WHERE menu_id=11025;  -- 保留 perms=djs:breed:dashboard:*

-- 2) 三 tab 各加一条专用导航权限子节点（F；仅用于 mp 底栏可见性，admin 不授）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
  (11026, '养殖·导航',   11021, 9, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:breed:nav:home',      '#', 1, NOW(), 'DJS-PERM-MENU-007 tab 可见性专用权限'),
  (11027, '疫苗药品·导航', 11024, 1, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:breed:nav:med',       '#', 1, NOW(), 'DJS-PERM-MENU-007 tab 可见性专用权限'),
  (11028, '养殖管理·导航', 11025, 1, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:breed:nav:dashboard', '#', 1, NOW(), 'DJS-PERM-MENU-007 tab 可见性专用权限');

-- 3) 授权：持有对应父 tab 节点的角色，补授其导航子节点
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, n.child
FROM sys_role_menu rm
JOIN (SELECT 11021 AS parent, 11026 AS child
      UNION ALL SELECT 11024, 11027
      UNION ALL SELECT 11025, 11028) n
  ON rm.menu_id = n.parent;
