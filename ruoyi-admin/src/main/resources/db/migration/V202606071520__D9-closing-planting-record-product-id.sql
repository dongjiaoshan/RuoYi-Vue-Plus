-- ============================================================
-- D9 closing — t_warehouse_planting_record 加 product_id
--
-- 背景：D9 _open-issues #18（WMS-VEG-001 raise）— vegetable_handle.product_id 在 mp 入库
--   流程一直为 NULL → stock_flow.product_id=0 兜底，破坏 product 维度查询。
--   决策 d (AI 推荐)：planting_record 加 product_id 冗余字段，由 PLT-PICK-001 / WMS-VEG-001 写入；
--   stock_flow.product_id 从 planting_record.product_id 拿，蔬菜追溯链路按 product 关联清晰。
--
-- 关联：
--   - D9 WMS-VEG-001 VegetableHandleServiceImpl.insertVegStockInFlow 改读 planting_record.product_id
--   - D10 PLT-PICK-001 采摘 service 写入种植记录时 set product_id
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE t_warehouse_planting_record
    ADD COLUMN product_id BIGINT NULL COMMENT '冗余产品 ID（PLT-PICK-001 / WMS-VEG-001 写入，FK → t_warehouse_product_info.id；用于 stock_flow / 追溯链按 product 关联）' AFTER crop_id;

ALTER TABLE t_warehouse_planting_record
    ADD INDEX idx_product_id (product_id);
