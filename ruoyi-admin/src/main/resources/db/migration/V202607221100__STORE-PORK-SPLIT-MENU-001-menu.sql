-- ============================================================
-- STORE-PORK-SPLIT-MENU-001  门店猪肉分割 独立菜单（白条现场分割生码）
-- ============================================================
-- 背景（邓博 2026-06-22 微信定调）：
--   · 「猪肉追溯码管理」(10520) 回归「猪肉追溯码列表 + 重打印」语义（前端 PorkTraceListPanel，
--     与果蔬追溯码管理一致）；
--   · 白条领到门店后的「现场分割 + 实时生码」独立为本菜单「门店猪肉分割」
--     （djs-store/pork-split/index → 复用 PorkTracePanel 卡面板：选确认收货白条 + 选门店打包间产品 + 录重量 → 追溯码打印）。
-- 复用权限串 djs:store:trace:gen（picker/生码，store 角色已授，无需新建 perm）。
-- 仅 INSERT sys_menu 一行 + INSERT IGNORE sys_role_menu 白名单；不删表/不删行。
-- 跑完 flush redis 菜单缓存（bash script/sql/djs/_post-init.sh 或重启 admin）。
-- ============================================================
SET NAMES utf8mb4;

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_dept, create_by, create_time, remark)
VALUES
  (10310, '门店猪肉分割', 10000, 6, 'pork-split', 'djs-store/pork-split/index', '',
   1, 0, 'C', '0', '0', 'djs:store:trace:gen', 'tool',
   NULL, 1, NOW(), '门店现场分割白条生码（STORE-PORK-SPLIT-001）');

-- 角色白名单：业务角色（NOT IN 超管1 / 租户管理员101）+ 超管 role_id=1，范式同 STORE-IA-5MENU-001
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 10310
FROM sys_role r
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0';

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10310);
