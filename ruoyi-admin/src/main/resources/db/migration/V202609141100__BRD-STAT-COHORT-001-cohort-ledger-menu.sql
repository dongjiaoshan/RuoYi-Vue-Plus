-- 配种批次去向台账菜单（BRD-STAT-COHORT-001）。
--
-- 甲方要「这批一共配种多少头，中间损失多少，分娩率为什么是这个数据」的逐批对账视图。
-- 挂养殖父菜单 7000 下，menu_id 取 dashboard 段 7400-7499 的 7401。
-- 权限串沿用 7400 的 djs:breed:dashboard:*（端点实际校验 djs:breed:dashboard:annual，Sa-Token 通配匹配），
-- 授权角色与 7400 一致：系统管理员 101 / 老板 102 / 管理人员 103 / 养殖管理员 104。
--
-- 幂等：INSERT IGNORE 按主键去重。
SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (7401, '配种批次对账', 7000, 21, 'cohort-ledger',
   'djs-breed/dashboard/cohort-ledger/index', '',
   1, 0, 'C', '0', '0', 'djs:breed:dashboard:*', 'tickets', 1, NOW(),
   '配种批次去向台账 + 超期未定性清单（BRD-STAT-COHORT-001）');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (101, 7401), (102, 7401), (103, 7401), (104, 7401);
