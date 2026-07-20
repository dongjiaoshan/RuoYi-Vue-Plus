-- ============================================================
-- PLT-DASH-RESTORE-001  恢复「种植看板」菜单（富图看板版，独立于种植总览）
-- ============================================================
-- Kevin 2026-06-15 拍板：种植总览(8112/djs-plant/overview/index) = 现有列表着陆页保留不动；
--   种植看板(本 ticket) = 新建富图仪表盘页（6 区块 + ECharts 双甘特），两页独立并存。
-- 旧 8110/8111 曾被 FIX-PLT-AD-OVERVIEW-001 删除（改建为种植总览 8112-8114），现重新占用 8110/8111。
-- 种植域 menu_id 段 8000-8999 / dashboard 子段 8110-8111（CLAUDE.md §6 种植段二级分段）。
-- visible='0' = 显示（ruoyi：0=显示 / 1=隐藏）；挂种植父菜单 8000，order_num=0 置顶。
-- component=djs-plant/dashboard/index，perm djs:plant:dashboard:view（be @SaCheckPermission 已存在）。
-- 跑完必须 flush redis 菜单缓存（bash script/sql/djs/_post-init.sh 或重启 admin）。
-- ============================================================

SET NAMES utf8mb4;

-- 8110 种植看板 C 菜单（顶层挂 8000，置顶）+ 8111 view F 权限。
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
   menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (8110, '种植看板', 8000, 0, 'dashboard', 'djs-plant/dashboard/index', '', 1, 0,
   'C', '0', '0', 'djs:plant:dashboard:view', 'dashboard', 1, NOW(), 'PLT-DASH-RESTORE-001 种植看板富图仪表盘'),
  (8111, '种植看板查看', 8110, 1, '', '', '', 1, 0,
   'F', '0', '0', 'djs:plant:dashboard:view', '#', 1, NOW(), 'PLT-DASH-RESTORE-001 view 权限');

-- 授权：照种植父菜单 8000 持有者范式（超管 role_id=1 绕鉴权，其余角色按 8000 同步）。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 8110 FROM sys_role_menu WHERE menu_id = 8000;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 8111 FROM sys_role_menu WHERE menu_id = 8000;

-- 超管 role_id=1 兜底（部分环境 8000 未给 role 1 绑定时，确保超管可见）。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 8110), (1, 8111);
