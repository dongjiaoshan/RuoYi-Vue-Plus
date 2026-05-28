-- ============================================================
-- D9 hotfix 采购入库（简化版）—— 菜单 + 字典扩 + 角色权限
--
-- 触发：D9 testing-human §5 实测包材领用时发现"没有任何包材库存"导致领用→库存不足
-- 处置：在 admin 仓库板块新增「采购入库」一个 admin-only 简化入口
--       UPSERT location_stock + INSERT stock_flow（flow_type=purchase_in / inout_type=IN）
--
-- !!! 重要 !!!
--   跑完本 SQL 必须刷 Redis 字典缓存（admin 启动过后 sys_dict 会缓存 NullValue）：
--     bash code/main/RuoYi-Vue-Plus/script/sql/djs/_post-init.sh
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 字典扩：djs_flow_type 新增 purchase_in（采购入库）
--    现有 13 值由 V202606071300__WMS-MAT-001-mat-flow.sql §2 seed
--    dict_code 续 102450013（102450012=other 之后）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (102450013, '1001', 13, '采购入库', 'purchase_in', 'djs_flow_type', '', 'primary', 'N', 1, NOW(), 'D9 hotfix 采购入库简化版');

-- ------------------------------------------------------------
-- 2. sys_menu seed
--    9060 父菜单「采购入库」（仓库 9000 子，在 9020 库存查询 / 9100 包材领用之间挪入；order_num 取 4 与现有
--    菜单序列对齐——9010 库位管理(1) / 9020 库存查询(2) / 9040 物资管理(3) / 9100 包材领用(6)）
--    9061-9063 三按钮（查询 / 新增 / 导出）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9060, '采购入库', 9000, 4, 'purchase-in', 'djs-warehouse/purchaseIn/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:purchaseIn:list', 'shopping-bag-3', 1, NOW(), 'D9 hotfix 采购入库简化版'),
    (9061, '采购入库查询', 9060, 1, '', '', '', 1, 0, 'F', '0', '0', 'djs:warehouse:purchaseIn:query',  '#', 1, NOW(), 'D9 hotfix'),
    (9062, '采购入库新增', 9060, 2, '', '', '', 1, 0, 'F', '0', '0', 'djs:warehouse:purchaseIn:add',    '#', 1, NOW(), 'D9 hotfix'),
    (9063, '采购入库导出', 9060, 3, '', '', '', 1, 0, 'F', '0', '0', 'djs:warehouse:purchaseIn:export', '#', 1, NOW(), 'D9 hotfix');

-- ------------------------------------------------------------
-- 3. 角色权限绑定
--    a) role_id=1（admin / 超管）—— 加 4 行
--    b) role_id=101（tenant admin 租户管理员）—— 加 4 行
--    c) 其他业务角色（mp / staff / 仓管等，role_id NOT IN (1,101) AND del_flag='0'）—— 4 行 × n 角色
--
--    INSERT IGNORE 保证幂等；现有 ruoyi 角色机制：role_id=1 通常已隐含全权限，但显式 sys_role_menu 行
--    与 V202606071300 §4.3 范式一致，便于审计。
-- ------------------------------------------------------------
-- 3.1 role_id=1
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 9060), (1, 9061), (1, 9062), (1, 9063);

-- 3.2 role_id=101
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (101, 9060), (101, 9061), (101, 9062), (101, 9063);

-- 3.3 其他业务角色（V1 单农场，role_id 范围有限；管理面所有非 1/101 角色都给）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 9060 AS menu_id UNION ALL
    SELECT 9061 UNION ALL
    SELECT 9062 UNION ALL
    SELECT 9063
) m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0';
