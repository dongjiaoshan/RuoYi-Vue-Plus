-- FIX-BRD-MP-EVENT-PERM-001：补养殖 mp 事件端点缺失的权限菜单 + 授权，并归位「养殖小程序」树。
--
-- 背景（真机 403「没有访问权限，请联系管理员授权」根因）
--   mp 配种/分娩/断奶/返空流/查情不配种/转移/死亡/淘汰 录入与列表端点的
--   @SaCheckPermission 用 djs:breed:event:{breeding,farrow,heat,transfer,die,eliminate}[:list]，
--   但这些 perm 从未 seed 进 sys_menu —— 任何真实登录角色（含养殖工人 108 / 养殖管理员 104）
--   都拿不到这些权限 → 真机 403。dev mock 登录走 *:*:* 通配，测不出（假绿）。
--   同类已正确 seed 的（引种 intro 7100 / 阉割 castrate 7150 / 出栏 slaughter 7153）正常工作。
--   断奶(weaning) / 返空流(nullreturn) / 查情不配种(heat) 三端点共用 djs:breed:event:heat。
--   注：这些是 djs:breed:event:* perm，不在 DJS-PERM-BOARD-001 的 djs:applet:breed:* 板块授权范围内，
--   故须显式建 menu + 授权（对齐 7150/7151/7153 的授权方式，BOARD-001 的 applet 清理段不波及）。
--
-- 生效
--   PermissionService 颁 token 时实时读 DB（无缓存，见 DJS-PERM-BOARD-001 注）：部署后用户重新登录即生效，
--   无需 flush redis、无需改前端。

-- 0) 清掉 7120-7139 旧 BRD-EVENT-002 配种 / BRD-EVENT-004 死淘转移 菜单合并入 7145 后残留的孤儿授权
--    （menu 已删、sys_role_menu 行未清，挂在 {102,103,104,109} 上，不清则与下方授权混叠、grant 集不确定）。
--    这些 menu_id 当前在 sys_menu 已不存在（解析不出任何 perm），DELETE 仅清表、零功能影响。
DELETE FROM sys_role_menu WHERE menu_id BETWEEN 7120 AND 7139;
DELETE FROM sys_menu      WHERE menu_id BETWEEN 7120 AND 7139;

-- 1) 补 6 类事件 F 型权限菜单，挂「养殖小程序」(11020) 树下（visible='1' 不进侧边栏，仅授权/菜单管理树可见；
--    perms 严格对齐各 controller @SaCheckPermission；格式对齐已存在的 7150/7151/7153）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
  (7120, 'mp 配种查询',        11020, 70, '', '', '', 1, 0, 'F', '1', '0', 'djs:breed:event:breeding:list',  '#', 1, NOW(), 'FIX-BRD-MP-EVENT-PERM-001'),
  (7121, 'mp 配种录入',        11020, 71, '', '', '', 1, 0, 'F', '1', '0', 'djs:breed:event:breeding',       '#', 1, NOW(), 'FIX-BRD-MP-EVENT-PERM-001'),
  (7122, 'mp 分娩查询',        11020, 72, '', '', '', 1, 0, 'F', '1', '0', 'djs:breed:event:farrow:list',    '#', 1, NOW(), 'FIX-BRD-MP-EVENT-PERM-001'),
  (7123, 'mp 分娩录入',        11020, 73, '', '', '', 1, 0, 'F', '1', '0', 'djs:breed:event:farrow',         '#', 1, NOW(), 'FIX-BRD-MP-EVENT-PERM-001'),
  (7124, 'mp 查情/断奶/返空查询', 11020, 74, '', '', '', 1, 0, 'F', '1', '0', 'djs:breed:event:heat:list',      '#', 1, NOW(), 'FIX-BRD-MP-EVENT-PERM-001 (查情/断奶/返空流共用 heat perm)'),
  (7125, 'mp 查情/断奶/返空录入', 11020, 75, '', '', '', 1, 0, 'F', '1', '0', 'djs:breed:event:heat',           '#', 1, NOW(), 'FIX-BRD-MP-EVENT-PERM-001 (查情/断奶/返空流共用 heat perm)'),
  (7130, 'mp 转移查询',        11020, 76, '', '', '', 1, 0, 'F', '1', '0', 'djs:breed:event:transfer:list',  '#', 1, NOW(), 'FIX-BRD-MP-EVENT-PERM-001'),
  (7131, 'mp 转移录入',        11020, 77, '', '', '', 1, 0, 'F', '1', '0', 'djs:breed:event:transfer',       '#', 1, NOW(), 'FIX-BRD-MP-EVENT-PERM-001'),
  (7132, 'mp 死亡查询',        11020, 78, '', '', '', 1, 0, 'F', '1', '0', 'djs:breed:event:die:list',       '#', 1, NOW(), 'FIX-BRD-MP-EVENT-PERM-001'),
  (7133, 'mp 死亡录入',        11020, 79, '', '', '', 1, 0, 'F', '1', '0', 'djs:breed:event:die',            '#', 1, NOW(), 'FIX-BRD-MP-EVENT-PERM-001'),
  (7134, 'mp 淘汰查询',        11020, 80, '', '', '', 1, 0, 'F', '1', '0', 'djs:breed:event:eliminate:list', '#', 1, NOW(), 'FIX-BRD-MP-EVENT-PERM-001'),
  (7135, 'mp 淘汰录入',        11020, 81, '', '', '', 1, 0, 'F', '1', '0', 'djs:breed:event:eliminate',      '#', 1, NOW(), 'FIX-BRD-MP-EVENT-PERM-001');

-- 2) 授权：对齐同类事件（intro 7100 / castrate 7150 / slaughter 7153）的角色集
--    {101 系统管理员, 102 老板, 103 管理人员, 104 养殖管理员, 108 养殖工人, 109 兽医}
--    （超管 role_id=1 走框架 *:*:* 通配，无需挂行）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id IN (101, 102, 103, 104, 108, 109)
  AND m.menu_id IN (7120, 7121, 7122, 7123, 7124, 7125, 7130, 7131, 7132, 7133, 7134, 7135);

-- 3) 归位（row4）：引种登记(7100) / 仔猪耳标(7110) / 生长记录(7140) 三个 mp 操作菜单从 admin「养殖」(7000)
--    树迁到「养殖小程序」(11020)。三者均 visible='1' 已隐藏于侧边栏、path 非空（intro/eartag/growth），
--    迁 parent 仅改授权/菜单管理树位置，perms 与路由不变；子节点 parent_id 仍指向各自父节点，随父迁移。
UPDATE sys_menu SET parent_id = 11020 WHERE menu_id IN (7100, 7110, 7140);
