-- ============================================================
-- D9 closing — t_warehouse_stock_flow 加 demand_id
--
-- 背景：D9 _open-issues #5（WMS-MAT-001 raise）— D14 CROSS-FLOW-003 设计预期通过
--   SUM(stock_flow.change_quantity WHERE flow_type='ship_out' AND demand_id=?)
--   算需求单 shipped_count；当前 stock_flow 无 demand_id 字段，必须 ALTER 加列 + 索引。
--   仅 ship_out 流水写入，其他写入入口（pick_out / return_in / loss / slaughter_burn /
--   veg_stock_in 等）此字段为 NULL。
--
-- 关联：D10 WMS-SHIP-001 service 写流水时 set demand_id；D14 CROSS-FLOW-003 listener
--   按本字段聚合 shipped_count。
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE t_warehouse_stock_flow
    ADD COLUMN demand_id BIGINT NULL COMMENT '关联需求单 ID（仅 ship_out 流水写入，FK → t_warehouse_demand_manage.id；D14 CROSS-FLOW-003 聚合 shipped_count）' AFTER warehouse_id;

ALTER TABLE t_warehouse_stock_flow
    ADD INDEX idx_demand_id (demand_id);
