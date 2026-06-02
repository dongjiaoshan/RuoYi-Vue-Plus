-- WMS-FLOW-001 fix：补 djs_inout_type 字典（出入库流水「出入」方向）。
--
-- 缺口：admin 出入库流水 / 打包物料 / 库存查询三页（stockFlow / matPack / stock）的「出入」
-- select + dict-tag 列均消费 dict_type='djs_inout_type'，但该字典从未 seed → 下拉「无数据」、
-- 列显裸 key（D12 人力测试 6.7 发现）。
--
-- 值对齐存储实况：t_warehouse_stock_flow.inout_type CHAR(3) 取 IN=入库 / OT=出库（StockFlow.java
-- javadoc + StockFlowController 强制锁值 + 全写入路径一致；非 doc/11 旧稿的 TINYINT 1/2）。
-- 仅 2 值（本流水方向只有进/出）。
--
-- 注意：另有 djs_stock_flow_type（出入库类型 IN/OUT/TRANSFER/GAIN/LOSS，SYS-INIT-002 seed）是更宽的
-- 语义集，值 OUT≠OT 且多 3 个无关项，不能给本流水「出入」复用；两者保持独立。
INSERT IGNORE INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (920530, '1001', '出入类型', 'djs_inout_type', 1, NOW(), 'WMS-FLOW-001 出入库流水方向 IN/OT');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (102453000, '1001', 0, '入库', 'IN', 'djs_inout_type', '', 'success', 'Y', 1, NOW(), 'WMS-FLOW-001'),
    (102453001, '1001', 1, '出库', 'OT', 'djs_inout_type', '', 'warning', 'N', 1, NOW(), 'WMS-FLOW-001');
