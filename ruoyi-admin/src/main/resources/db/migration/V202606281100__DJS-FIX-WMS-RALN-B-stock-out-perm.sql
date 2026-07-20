-- ============================================================
-- DJS-FIX-WMS-RALN-B  库存查询行「产品出库」按钮权限
-- ============================================================
-- 原型：库存管理/库存查询 行操作「产品出库」→ 弹窗（出库日期 / 出库量 / 出库方式）→ 确定出库。
-- 后端端点：POST /djs/warehouse/stock/out（@SaCheckPermission("djs:warehouse:stock:out")）。
-- menu_id 分段（CLAUDE.md §6.6 仓库 9000-9999）：库存查询 9020-9025；现占 9020(C)/9021(list F)/
--   9025(export F)，本轮新增 9022（产品出库 F），9023-9024 仍空。
-- 字典：出库方式走已有 djs_stock_out_dest（V202606081500 起已 seed 6 值），不新增字典。
-- 幂等：F 按钮 + role_menu 全 INSERT IGNORE（重复跑等价）。
-- sys_menu / sys_role_menu 为 ruoyi 框架表，无 tenant_id（多租户走 sys_role_menu），不加 tenant_id。
-- role_menu 白名单范式：role_id NOT IN (1,101) AND del_flag='0' + 超管 role_id=1 全绑
--   （抄 V202606280900）。
-- ============================================================

SET NAMES utf8mb4;

-- 库存查询行「产品出库」F 按钮（9022，挂 9020 库存查询）
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9022, '产品出库', 9020, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:stock:out', '#', 1, NOW(), 'DJS-FIX-WMS-RALN-B');

-- 绑业务角色白名单（与仓库其余菜单同范围）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 9022
FROM sys_role r
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0';

-- 超级管理员角色 1 全绑
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 9022);
