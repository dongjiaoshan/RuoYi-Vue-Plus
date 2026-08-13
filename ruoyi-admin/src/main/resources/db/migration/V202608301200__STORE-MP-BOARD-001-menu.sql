-- STORE-MP-BOARD-001（V6 row62/63）：门店小程序版块重新上线，在 mp 权限树「门店小程序」(11050) 下建
-- tab 与功能节点，并授给门店管理员 / 门店店员。
--
-- 结构
--   11050 门店小程序（已存在，visible='1' 只在角色授权树可见、不进 admin 侧边栏）
--     └ 11051 需求下单       M  path=mp-store-demand  perms=djs:mptab:store:demand   ← 板块 tab 显隐开关
--         └ 11052 门店需求·功能 F                      perms=djs:applet:store:demand:* ← mp 门店需求全部接口
--
-- 为什么 11052 必须挂在 11051 **下面**，不能跟它平级
--   plus-ui 角色配置树对 mp 权限走 `buildMpTabLevelTree`：tab 级节点（depth 2）的后代会被折叠进
--   `hiddenDescendants`，勾/取消 tab 时自动带上它们。挂成平级会得到两个后果，都踩甲方原话：
--     ① 树上会多出「门店需求·功能」这个内部技术名节点 —— 甲方要的是「目前仅有：需求下单功能菜单」；
--     ② 管理员取消勾「需求下单」时，11052 仍被保留 → 它的 perms 以 `djs:applet:store` 开头，
--        `UserBoardController.mapPermsToBoards` 照样判出 store 板块 → 门店板块**不消失**，
--        而是退化成只剩「我的」一个 tab 的空壳板块（落地页也变成「我的」）。
--   这正是 FIX-DJS-PERM-MENU-009「取消勾选压不住」换了个形态复发。挂成子节点后，
--   11051 就是唯一的 toggle：勾上两个都授、取消两个都撤，板块跟着整体消失。
--   （与既有 mp tab 形态一致：11021→11022/11023/11026、11032→11033/11034、11061→11062/11063。）
--
-- path 不能为空：ruoyi /getRouters 把 M/C 菜单全下发为路由（visible='1' 只隐藏侧边栏、不排除路由），
-- 空 path 到 vue-router addRoute 判 `path[0] !== '/'` 时崩（FIX-DJS-PERM-MENU-001）。F 型不产生路由，留空。
--
-- 授权（ADR-0020 §2.2「授权必授整子树含父目录」）
--   107 store_admin / 112 store_clerk：11000 + 11050 + 11051 + 11052
--   另补 11010 + 5350（djs:applet:common:store:list）—— mp「我的门店」端点
--   GET /djs/applet/common/store/my-stores 复用这个既有 broad perm，两个门店角色原先都没有它，
--   不补就是 403。
--   role_id=1 超管一并授（它走 *:*:* 不依赖这些行，纯为角色树勾选状态一致）。
--
-- 生效：mp 侧改权限后**必须重新登录**（perms 在登录时快照进 Sa-Token 会话），不用 flush redis。

INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
  (11051, '需求下单',      11050, 1, 'mp-store-demand', NULL, '', 1, 0, 'M', '1', '0', 'djs:mptab:store:demand',    'shopping',  1, NOW(), 'STORE-MP-BOARD-001 门店板块 tab 显隐（唯一 toggle，功能叶子 11052 挂其下）'),
  (11052, '门店需求·功能', 11051, 1, '',                NULL, '', 1, 0, 'F', '1', '0', 'djs:applet:store:demand:*', '#',         1, NOW(), 'STORE-MP-BOARD-001 mp 门店需求接口权限（挂 11051 下，随 tab 一起授/撤）');

-- 幂等：PK(role_id,menu_id) 冲突忽略（部分环境可能已手工授过父目录）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
  (1,   11000), (1,   11010), (1,   5350), (1,   11050), (1,   11051), (1,   11052),
  (107, 11000), (107, 11010), (107, 5350), (107, 11050), (107, 11051), (107, 11052),
  (112, 11000), (112, 11010), (112, 5350), (112, 11050), (112, 11051), (112, 11052);

-- 验证（按需手动跑）
-- SELECT menu_id, menu_name, parent_id, menu_type, path, perms FROM sys_menu WHERE menu_id IN (11050,11051,11052);
-- SELECT menu_id, menu_name FROM sys_menu WHERE parent_id = 11051;  -- 必须只有 11052（功能叶子挂 tab 下）
-- SELECT role_id, GROUP_CONCAT(menu_id ORDER BY menu_id) FROM sys_role_menu
--  WHERE role_id IN (1,107,112) AND menu_id IN (5350,11000,11010,11050,11051,11052) GROUP BY role_id;
