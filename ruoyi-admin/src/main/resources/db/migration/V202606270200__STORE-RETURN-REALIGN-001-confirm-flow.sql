-- ============================================================
-- STORE-RETURN-REALIGN-001  退回管理对齐原型：两段式确认 + 退回操作页
-- ============================================================
-- 原型「退回管理」= 退回操作（猪肉/果蔬 tab 矩阵批量录退回重量）+ 退回记录
--   （只读列表 + 仓库确认实收两段态：待仓库确认 → 已入库）。
-- 本迁移：① t_store_return 补 货物重量 + 两段式确认列；② djs_store_return_status 字典；
--         ③ 菜单 退回操作（C）+ 退回确认（F）。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_store_return 补列（货物重量 + 仓库实收 + 确认态）
--    location_id 已可空（STR-RETURN-REBUILD-001），两段式下确认时才填库位。
-- ------------------------------------------------------------
ALTER TABLE t_store_return
    ADD COLUMN goods_weight    DECIMAL(12,2) NULL COMMENT '报退货物重量(kg)（原型「货物重量」）',
    ADD COLUMN return_status   VARCHAR(16)  NOT NULL DEFAULT 'pending'
        COMMENT '退货状态 djs_store_return_status：pending=待仓库确认 / received=已入库',
    ADD COLUMN received_qty    DECIMAL(12,2) NULL COMMENT '仓库实收量（仓库确认时填）',
    ADD COLUMN received_weight DECIMAL(12,2) NULL COMMENT '仓库实收重量(kg)（仓库确认时填）',
    ADD COLUMN confirm_user_id BIGINT        NULL COMMENT '仓库确认人 user_id',
    ADD COLUMN confirm_time    DATETIME      NULL COMMENT '仓库确认时间';

-- 存量行（历史单条直登）视为已入库，回填 received，避免列表全落「待仓库确认」
UPDATE t_store_return SET return_status = 'received' WHERE return_status = 'pending' AND del_flag = '0' AND location_id IS NOT NULL;

-- ------------------------------------------------------------
-- 2. djs_store_return_status 字典（2 态，对齐原型文案 待仓库确认 / 已入库）
-- ------------------------------------------------------------
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, remark)
VALUES (920650, '1001', '门店退货状态', 'djs_store_return_status', 1, 'STORE-RETURN-REALIGN-001 两段式 2 态');

INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, remark)
VALUES
    (92065001, '1001', 1, '待仓库确认', 'pending',  'djs_store_return_status', '', 'warning', 'Y', 1, '退回操作提交后初始态'),
    (92065002, '1001', 2, '已入库',     'received', 'djs_store_return_status', '', 'success', 'N', 1, '仓库确认实收 + 联动外购入库');

-- ------------------------------------------------------------
-- 3. 菜单：退回操作（C，10360，挂退回管理 10601）+ 退回确认（F，10356，挂退回记录 10350）
--    退回记录 10350 已由 STORE-REALIGN-IA-001 改名 + order_num=2；退回操作 order_num=1 排前。
-- ------------------------------------------------------------
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (10360, '退回操作', 10601, 1, 'operation', 'djs-store/return/operation/index', '',
     1, 0, 'C', '0', '0', 'djs:store:return:add', 'edit', 1, NOW(), 'STORE-RETURN-REALIGN-001 退回操作矩阵录入'),
    (10356, '退回确认', 10350, 6, '', '', '',
     1, 0, 'F', '0', '0', 'djs:store:return:confirm', '#', 1, NOW(), 'STORE-RETURN-REALIGN-001 仓库确认实收');

-- role_menu 白名单（抄 STR-DEMAND-001 范式：业务角色 + 超管）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id IN (10356, 10360);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id FROM sys_menu m WHERE m.menu_id IN (10356, 10360);
