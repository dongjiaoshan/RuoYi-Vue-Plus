-- ============================================================
-- FIX-DJS-PERM-MENU-011  「播种」+ 管理板块 3 tab 接上角色树
-- ============================================================
-- 背景（Kevin 2026-08-13）：权限只由勾选决定、与角色名无关。FIX-DJS-PERM-MENU-010 修完燎毛间后
-- 复查，mp 还剩 4 个 tab 完全不可控 —— 前端无 gate、后端只 @SaCheckLogin、角色树里连勾选框都没有：
--   种植板块 tab1「播种」            （pages/plant/index，端点 monthTasks / seedSummary 均 @SaCheckLogin）
--   管理板块「养殖管理/种植管理/仓库管理」（pages/{breed,plant,warehouse}/dashboard/index）
--
-- 解法：补 3 个独立导航命名空间节点（与 djs:mptab:breed:* / djs:mptab:store:demand 同一套路，
--   放在 djs:applet:* 端点通配之外，避免 permMatch 双向通配互相命中 —— FIX-DJS-PERM-MENU-008 的坑）：
--     11035 播种     → djs:mptab:plant:sow
--     11036 种植管理  → djs:mptab:plant:dashboard
--     11043 仓库管理  → djs:mptab:warehouse:dashboard
--   管理板块的「养殖管理」不新建节点，复用养殖板块已有的 11025 djs:mptab:breed:dashboard ——
--   同一个 dashboard 页在两个板块里用同一把锁，顺带堵掉「从管理板块绕过养殖管理授权」的口子。
--
-- 授权：按"当前谁看得见就授给谁"派生，行为零变化，甲方从此可勾可取消。
--   不硬编码 role_id —— 甲方自建角色（燎毛工/蔬菜处理工/盘点员/司机）是雪花 id，
--   staging 与 prod 不一致，硬编码会在 prod 授错角色。
--   播种     → 能进种植板块的角色（持任一 djs:{applet,mptab}:plant 权限）
--   两个管理 → 能进管理板块的角色（breed/plant/warehouse 三域齐全，对齐 UserBoardController.mapPermsToBoards）
--
-- 配套：miniapp/src/tabbar/config.ts plantTabbarList「播种」+ manageTabbarList 三 tab 加 permissions。
-- 生效：PermissionService 颁 token 实时读 DB，mp 重新登录即生效；无需 flush redis。
-- ============================================================

-- 1) 三个导航权限节点（menu_type='F'：纯权限叶子，无 admin 页面，同 11026「养殖·导航」）
--    播种 order_num=0 让它排在「采收」(1) 前面，与 mp 底栏 tab 顺序一致
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
  (11035, '播种',   11030, 0, '', '', '', 1, 0, 'F', '1', '0', 'djs:mptab:plant:sow',            '#', 1, NOW(), 'FIX-DJS-PERM-MENU-011 种植板块 tab1 显隐'),
  (11036, '种植管理', 11030, 3, '', '', '', 1, 0, 'F', '1', '0', 'djs:mptab:plant:dashboard',      '#', 1, NOW(), 'FIX-DJS-PERM-MENU-011 管理板块「种植管理」tab 显隐'),
  (11043, '仓库管理', 11040, 5, '', '', '', 1, 0, 'F', '1', '0', 'djs:mptab:warehouse:dashboard',  '#', 1, NOW(), 'FIX-DJS-PERM-MENU-011 管理板块「仓库管理」tab 显隐')
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), perms = VALUES(perms), menu_type = VALUES(menu_type);

-- 2) 授「播种」给能进种植板块的角色
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT t.role_id, 11035 FROM (
  SELECT DISTINCT rm.role_id
  FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id
  WHERE m.perms LIKE 'djs:applet:plant%' OR m.perms LIKE 'djs:mptab:plant%'
) t;

-- 3) 授「种植管理」「仓库管理」给能进管理板块的角色（三域齐全）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT t.role_id, n.menu_id FROM (
  SELECT rm.role_id
  FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id
  GROUP BY rm.role_id
  HAVING SUM(m.perms LIKE 'djs:applet:breed%'     OR m.perms LIKE 'djs:mptab:breed%')     > 0
     AND SUM(m.perms LIKE 'djs:applet:plant%'     OR m.perms LIKE 'djs:mptab:plant%')     > 0
     AND SUM(m.perms LIKE 'djs:applet:warehouse%' OR m.perms LIKE 'djs:mptab:warehouse%') > 0
) t
CROSS JOIN (SELECT 11036 AS menu_id UNION ALL SELECT 11043) n;
