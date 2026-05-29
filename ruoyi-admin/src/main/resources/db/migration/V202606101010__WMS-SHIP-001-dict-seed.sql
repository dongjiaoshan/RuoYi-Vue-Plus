-- ============================================================
-- WMS-SHIP-001 字典 seed
--
-- 新增 3 字典：
--   djs_shipment_status   4 态：pending / checking / shipped / delivered
--   djs_return_direction  3 态：customer_to_store / store_to_warehouse / warehouse_to_supplier
--   djs_return_status     3 态：pending / confirmed / rejected
--
-- 复用（不重复 seed）：
--   djs_deliver_type  (D8 WMS-DEMAND-001 / D7 WMS-MD-001 已 seed)
--   djs_yes_no
--
-- 约定（ADR-0004）：
--   dict_type 前缀 djs_ / dict_value snake_case 字典码 / dict_id 920600+ 段（920520 之后）
-- ============================================================

SET NAMES utf8mb4;

-- ============== 1. djs_shipment_status 4 态 ==============
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, remark)
VALUES (920601, '1001', '发货状态', 'djs_shipment_status', 1, 'WMS-SHIP-001 4 态');

INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, remark)
VALUES
    (92060101, '1001', 1, '待发货', 'pending',   'djs_shipment_status', '', 'info',    'Y', 1, '初始态'),
    (92060102, '1001', 2, '清点中', 'checking',  'djs_shipment_status', '', 'warning', 'N', 1, '工人正在清点'),
    (92060103, '1001', 3, '已发车', 'shipped',   'djs_shipment_status', '', 'primary', 'N', 1, '清点完成 + 上车'),
    (92060104, '1001', 4, '已到货', 'delivered', 'djs_shipment_status', '', 'success', 'N', 1, '门店签收（CROSS-FLOW-003 触发）');

-- ============== 2. djs_return_direction 3 态 ==============
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, remark)
VALUES (920602, '1001', '退货方向', 'djs_return_direction', 1, 'WMS-SHIP-001 doc/06 默认三方向；V1 仅 store_to_warehouse 联动 stock_flow');

INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, remark)
VALUES
    (92060201, '1001', 1, '顾客→门店',    'customer_to_store',     'djs_return_direction', '', 'info',    'N', 1, 'V1 仅录入占位，不联动 stock_flow'),
    (92060202, '1001', 2, '门店→仓库',    'store_to_warehouse',    'djs_return_direction', '', 'primary', 'Y', 1, 'V1 主路径，联动 stock_flow return_in'),
    (92060203, '1001', 3, '仓库→供应商', 'warehouse_to_supplier', 'djs_return_direction', '', 'warning', 'N', 1, 'V1 仅录入占位，不联动 stock_flow');

-- ============== 3. djs_return_status 3 态 ==============
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, remark)
VALUES (920603, '1001', '退货状态', 'djs_return_status', 1, 'WMS-SHIP-001 3 态');

INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, remark)
VALUES
    (92060301, '1001', 1, '待确认', 'pending',   'djs_return_status', '', 'info',    'Y', 1, 'mp 工人 / 顾客提交后初始态'),
    (92060302, '1001', 2, '已确认', 'confirmed', 'djs_return_status', '', 'success', 'N', 1, 'admin 复核通过 + 联动 stock_flow'),
    (92060303, '1001', 3, '已驳回', 'rejected',  'djs_return_status', '', 'danger',  'N', 1, 'admin 驳回不入库');
