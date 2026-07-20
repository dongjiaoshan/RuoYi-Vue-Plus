-- 白条 burn_id 半只贯穿（row13 · P1 地基）：库存/流水/领用/分割/发货 各表补 burn_id（半只白条唯一标识）。
-- burn_id 源头 = t_warehouse_pig_burn_record.burn_id（一白条一 burn_id，一耳可拆多白条，各独立记录）。
-- 全部 NULLABLE：外购白条 / 旧数据无 burn_id 时回落 ear_no / white_bar_id 现有路径，不 backfill。
-- 类型 VARCHAR(32) 对齐 t_warehouse_pig_burn_record.burn_id。

ALTER TABLE t_warehouse_product_inhouse   ADD COLUMN burn_id VARCHAR(32) NULL COMMENT '燎毛批次号（半只白条唯一标识，FK t_warehouse_pig_burn_record.burn_id）';
ALTER TABLE t_warehouse_location_stock     ADD COLUMN burn_id VARCHAR(32) NULL COMMENT '燎毛批次号（半只白条唯一标识）';
ALTER TABLE t_warehouse_stock_flow         ADD COLUMN burn_id VARCHAR(32) NULL COMMENT '燎毛批次号（半只白条唯一标识）';
ALTER TABLE t_warehouse_pig_cut_record     ADD COLUMN burn_id VARCHAR(32) NULL COMMENT '燎毛批次号（半只白条唯一标识）';
ALTER TABLE t_warehouse_loss_flow          ADD COLUMN burn_id VARCHAR(32) NULL COMMENT '燎毛批次号（半只白条唯一标识，滴水损失按半只）';
ALTER TABLE t_warehouse_product_production ADD COLUMN burn_id VARCHAR(32) NULL COMMENT '燎毛批次号（半只白条唯一标识，发货带半只）';

CREATE INDEX idx_burn ON t_warehouse_product_inhouse   (tenant_id, burn_id);
CREATE INDEX idx_burn ON t_warehouse_location_stock     (tenant_id, burn_id);
CREATE INDEX idx_burn ON t_warehouse_stock_flow         (tenant_id, burn_id);
CREATE INDEX idx_burn ON t_warehouse_pig_cut_record     (tenant_id, burn_id);
CREATE INDEX idx_burn ON t_warehouse_loss_flow          (tenant_id, burn_id);
CREATE INDEX idx_burn ON t_warehouse_product_production (tenant_id, burn_id);
