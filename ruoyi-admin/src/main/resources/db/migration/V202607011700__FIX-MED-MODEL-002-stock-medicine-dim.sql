-- FIX-MED-MODEL-002 location_stock 扩 medicine 维（ADR-0012 药品归仓库库位统一）
-- 给 t_warehouse_location_stock 加 medicine_id 第 4 维，承载药品库存（药品入库/领用扣减落仓库库存，不再用养殖 current_stock/batch.quantity）。
-- 4 维互斥（product_id / ear_no / plot_id / medicine_id 有且仅一非空）在 service 层校验。

ALTER TABLE t_warehouse_location_stock
    ADD COLUMN medicine_id BIGINT NULL COMMENT '药品维：药品库存按药品关联 FK → t_breed_medicine_info.id（与 product_id/ear_no/plot_id 四选一互斥）' AFTER plot_id;

ALTER TABLE t_warehouse_location_stock
    ADD KEY idx_medicine (tenant_id, medicine_id);
