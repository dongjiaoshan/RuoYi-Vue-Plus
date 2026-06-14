-- ============================================================
-- 0613-04  门店端需求状态字典：新建门店专用 5 态字典 djs_store_demand_status
-- ============================================================
-- 门店端需求复用仓库表 t_warehouse_demand_manage（7 态 demand_status），但门店视角只关心 5 态：
--   待确认(SUBMITTED) / 已确认(CONFIRMED) / 已发货(SHIPPED) / 确认到店(ARRIVED) / 已删除(DELETED)。
-- 不动共享字典 djs_demand_status（仓库侧仍用 7 态）；门店列表 dict-tag 指向本字典 + VO 派生字段
-- storeDemandStatus（service queryPageList 按 demand_status/received_time 算出）。
--   SUBMITTED→待确认 / CONFIRMED→已确认 / PARTIAL_SHIPPED|COMPLETED→已发货 /
--   CONFIRMED|已发货 且 received_time!=null→确认到店(ARRIVED) / DELETED|CANCELLED→已删除。
-- 幂等：先 DELETE 整个 dict_type 再全量重插。
-- migration 后跑 script/sql/djs/_post-init.sh flush redis 字典缓存。
-- ============================================================
SET NAMES utf8mb4;

DELETE FROM sys_dict_data WHERE dict_type = 'djs_store_demand_status';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_store_demand_status';

INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (920403, '1001', '门店需求状态', 'djs_store_demand_status', 1, NOW(), '门店视角 5 态派生字典（0613-04）');

INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
  (9204030, '1001', 0, '待确认', 'SUBMITTED', 'djs_store_demand_status', '', 'warning', 'N', 1, NOW(), '仓库 SUBMITTED 映射'),
  (9204031, '1001', 1, '已确认', 'CONFIRMED', 'djs_store_demand_status', '', 'primary', 'N', 1, NOW(), '仓库 CONFIRMED 未收货'),
  (9204032, '1001', 2, '已发货', 'SHIPPED',   'djs_store_demand_status', '', 'success', 'N', 1, NOW(), '仓库 PARTIAL_SHIPPED/COMPLETED 未收货'),
  (9204033, '1001', 3, '确认到店', 'ARRIVED',  'djs_store_demand_status', '', 'success', 'N', 1, NOW(), '已收货 received_time!=null'),
  (9204034, '1001', 4, '已删除', 'DELETED',   'djs_store_demand_status', '', 'info',    'N', 1, NOW(), '仓库 DELETED/CANCELLED 映射');
