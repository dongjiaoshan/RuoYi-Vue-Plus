-- ============================================================
-- DENGBO-R30  门店损耗记录：admin 菜单 + 损耗类型字典 seed
-- ============================================================
-- 门店管理（分组 M 10600）下新增「门店损耗记录」列表页（C）：读 t_store_loss_record，
--   按当前门店（Current-Store-Id 头行级注入）过滤，展示门店日损耗 + 白条分割损耗两类。
-- menu_id 段：门店域 10000-10999，门店管理组 10600 下现占 产品管理(10210)/门店盘点(10200)；
--   取门店段空号 10650（10650-10699 空），C 菜单挂 10600。
-- 损耗类型字典 djs_store_loss_type：列表/下拉展示 + 白条分割损耗汇总行 productName 占位来源。
--   dict_id 920430；dict_data code 9204300/9204301（沿用门店字典 92043xx 段，与 demand 920403 不撞）。
-- sys_menu / sys_role_menu 为 ruoyi 框架表，无 tenant_id。跑完 flush redis（_post-init.sh 或重启 admin）。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 损耗类型字典 djs_store_loss_type（幂等：先删整 type 再全量重插）
-- ------------------------------------------------------------
DELETE FROM sys_dict_data WHERE dict_type = 'djs_store_loss_type';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_store_loss_type';

INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (920430, '1001', '门店损耗类型', 'djs_store_loss_type', 1, NOW(), '门店损耗记录类型（DENGBO-R30）');

INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
  (9204300, '1001', 0, '门店日损耗',   'store_daily_loss',      'djs_store_loss_type', '', 'warning', 'N', 1, NOW(), '盘点台账 loss_qty 逐产品搬入'),
  (9204301, '1001', 1, '白条分割损耗', 'white_bar_split_loss',  'djs_store_loss_type', '', 'danger',  'N', 1, NOW(), '到店重−退回入库重 钳 0，按门店汇总一行');

-- ------------------------------------------------------------
-- 2. sys_menu seed 10650：门店损耗记录（C，挂门店管理组 10600）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (10650, '门店损耗记录', 10600, 3, 'loss', 'djs-store/loss/index', '',
     1, 0, 'C', '0', '0',
     'djs:storeLoss:list', 'skill', 1, NOW(), 'DENGBO-R30');

-- ------------------------------------------------------------
-- 3. role_menu 白名单绑定（业务角色 role_id NOT IN (1) 全绑 + 超管 role_id=1 全绑）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1)
  AND r.del_flag = '0'
  AND m.menu_id = 10650;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id = 10650;
