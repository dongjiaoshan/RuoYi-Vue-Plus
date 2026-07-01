-- 白条 burn_id 半只贯穿 · 修正（row13 · 邓博/Kevin 定 方案 A）：
-- 下游 6 表的标识列正名 burn_id → white_bar_no「白条流水号」= 每个白条(半只/整只)唯一标识（燎毛按白条生成 BAR_NO）。
-- 说明：burn_id 语义是「燎毛批次号」（一次燎毛提交一个，区分不了同批 2 个半只），不适合当半只键；
--       真正区分同猪两半只的是每白条唯一的「白条流水号」。t_warehouse_pig_burn_record.burn_id（燎毛批次源头）保留不动。
-- 结算/聚合仍按 white_bar_id(整猪)，white_bar_no 仅作半只标识 / 领用 / 展示 / 追溯。

ALTER TABLE t_warehouse_product_inhouse   CHANGE COLUMN burn_id white_bar_no VARCHAR(32) NULL COMMENT '白条流水号（半只/整只白条唯一标识，燎毛按白条生成）';
ALTER TABLE t_warehouse_location_stock     CHANGE COLUMN burn_id white_bar_no VARCHAR(32) NULL COMMENT '白条流水号（半只/整只白条唯一标识）';
ALTER TABLE t_warehouse_stock_flow         CHANGE COLUMN burn_id white_bar_no VARCHAR(32) NULL COMMENT '白条流水号（半只/整只白条唯一标识）';
ALTER TABLE t_warehouse_pig_cut_record     CHANGE COLUMN burn_id white_bar_no VARCHAR(32) NULL COMMENT '白条流水号（半只/整只白条唯一标识）';
ALTER TABLE t_warehouse_loss_flow          CHANGE COLUMN burn_id white_bar_no VARCHAR(32) NULL COMMENT '白条流水号（半只/整只白条唯一标识，滴水损失记录追溯）';
ALTER TABLE t_warehouse_product_production CHANGE COLUMN burn_id white_bar_no VARCHAR(32) NULL COMMENT '白条流水号（半只/整只白条唯一标识，发货带上门店按它识别到货半只）';

ALTER TABLE t_warehouse_product_inhouse   RENAME INDEX idx_burn TO idx_white_bar_no;
ALTER TABLE t_warehouse_location_stock     RENAME INDEX idx_burn TO idx_white_bar_no;
ALTER TABLE t_warehouse_stock_flow         RENAME INDEX idx_burn TO idx_white_bar_no;
ALTER TABLE t_warehouse_pig_cut_record     RENAME INDEX idx_burn TO idx_white_bar_no;
ALTER TABLE t_warehouse_loss_flow          RENAME INDEX idx_burn TO idx_white_bar_no;
ALTER TABLE t_warehouse_product_production RENAME INDEX idx_burn TO idx_white_bar_no;
