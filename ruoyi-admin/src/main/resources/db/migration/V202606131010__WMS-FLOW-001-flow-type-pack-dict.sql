-- WMS-FLOW-001 hotfix：djs_flow_type 字典补 pack_in / pack_consume 两值。
--
-- D10 打包工序（ProductProductionServiceImpl）写 stock_flow.flow_type='pack_in'（成品入库）
-- 与 'pack_consume'（礼盒组件消耗出库），但 WMS-MAT-001 初版 djs_flow_type 13 值 + D9 hotfix
-- purchase_in 共 14 值里都没收录这两个 → admin「入库/出库记录」类型列 dict-tag 翻不出，显示
-- 裸 key `pack_in` / `pack_consume`（D11 WMS-FLOW-001 人力测试 3.2 发现）。
--
-- pack_in   = 打包入库（IN，绿 success，与其它入库一致）
-- pack_consume = 打包消耗（OUT，橙 warning，与领用/分割出库一致）
INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (102450014, '1001', 14, '打包入库', 'pack_in',      'djs_flow_type', '', 'success', 'N', 1, NOW(), 'WMS-FLOW-001 补 D10 打包成品入库'),
    (102450015, '1001', 15, '打包消耗', 'pack_consume', 'djs_flow_type', '', 'warning', 'N', 1, NOW(), 'WMS-FLOW-001 补 D10 礼盒组件消耗出库');
