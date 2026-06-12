-- ============================================================
-- STORE-DEMAND-REALIGN-001  门店需求下单对齐原型
-- ============================================================
-- 原型「需求下单」= 需求列表（补 需求类型/产品规格/备注/预计到店重量/确认时间/确认人 列 + 确认收货）
--   + 新增需求（多产品购物车整页：7 业态 tab + stepper + 购物车侧栏 + 个人邮寄标记 + 整单 batch 提交）。
-- 本迁移：① t_warehouse_demand_manage 补 需求类型 / 预计到店重量 / 门店收货确认 列（均 additive nullable）；
--         ② djs_demand_mailing_type 字典（门店 / 个人邮寄）；
--         ③ 菜单：新增需求页（C，10010，列表按钮路由进入，隐藏）+ 确认收货按钮（F，10011）。
-- 说明：创建页 7 业态 tab（白条/猪肉/果蔬/干货/鸡蛋/礼盒/其他）是前端按 djs_belong_type 对产品分组的展示维度；
--       落库 product_type 仍走仓库域 4 业态（white_bar/vegetable/gift_box/other，service 端按产品 belongType 映射），
--       不扩 djs_demand_product_type 字典、不动仓库域状态机/编码逻辑（薄封装铁律）。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_warehouse_demand_manage 补列（均 nullable additive，存量行不受影响）
--    demand_type     需求类型（门店自取 / 个人邮寄）
--    expected_weight 预计到店重量(kg)（原型列「预计到店重量」）
--    received_time   门店收货确认时间（门店侧「确认收货」）
--    received_by     门店收货确认人 user_id
-- ------------------------------------------------------------
ALTER TABLE t_warehouse_demand_manage
    ADD COLUMN demand_type     VARCHAR(16)   NULL COMMENT '需求类型 djs_demand_mailing_type: store门店/mailing个人邮寄',
    ADD COLUMN expected_weight DECIMAL(12,2) NULL COMMENT '预计到店重量(kg)',
    ADD COLUMN received_time   DATETIME      NULL COMMENT '门店收货确认时间（门店侧确认收货）',
    ADD COLUMN received_by     BIGINT        NULL COMMENT '门店收货确认人 user_id';

-- ------------------------------------------------------------
-- 2. djs_demand_mailing_type 字典（2 类，对齐原型文案 门店 / 个人邮寄）
-- ------------------------------------------------------------
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, remark)
VALUES (920660, '1001', '需求邮寄类型', 'djs_demand_mailing_type', 1, 'STORE-DEMAND-REALIGN-001 门店/个人邮寄');

INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, remark)
VALUES
    (92066001, '1001', 1, '门店',     'store',   'djs_demand_mailing_type', '', 'primary', 'Y', 1, '门店自取/配送'),
    (92066002, '1001', 2, '个人邮寄', 'mailing', 'djs_demand_mailing_type', '', 'warning', 'N', 1, '个人邮寄');

-- ------------------------------------------------------------
-- 3. 菜单：新增需求页（C，10010，挂门店需求父 10001）+ 确认收货按钮（F，10011，挂需求录入 10002）
--    10010 visible='1' 隐藏（不进侧栏，仅列表「新增需求」按钮路由跳入）；component 指向购物车整页。
--    role_menu 白名单范式：role_id NOT IN (1,101) AND del_flag='0' + 超管 role_id=1 全绑。
-- ------------------------------------------------------------
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (10010, '新增需求', 10001, 2, 'create', 'djs-store/demand/create/index', '',
     1, 0, 'C', '1', '0', 'djs:store:demand:add', 'plus', 1, NOW(), 'STORE-DEMAND-REALIGN-001 购物车整页（隐藏，列表按钮路由进入）'),
    (10011, '确认收货', 10002, 8, '', '', '',
     1, 0, 'F', '0', '0', 'djs:store:demand:receive', '#', 1, NOW(), 'STORE-DEMAND-REALIGN-001 门店收货确认');

-- role_menu 白名单（业务角色 + 超管）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id IN (10010, 10011);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id FROM sys_menu m WHERE m.menu_id IN (10010, 10011);
