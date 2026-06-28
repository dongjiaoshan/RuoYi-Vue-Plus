-- ============================================================
-- FIX-WMS-BAR-CUT-FIELDS-001  白条表加分割产品重量 + 分割损耗（邓博 admin row8）
-- ============================================================
-- 邓博 6/25 行8：白条表增加「分割产品重量」（白条分割成产品后总重）+「分割损耗」
--   （分割完成时算 = 出库重量 − 分割品重量）。
-- 原仅 compute-on-read（PigCutRecordVo 上算，admin 分割记录页展示）；现 cutDone 结算点
--   持久化到 bar_info，口径与 loss_flow(djs_loss_type=cut) 双写一致。
-- ============================================================

ALTER TABLE t_warehouse_bar_info
    ADD COLUMN cut_product_weight DECIMAL(12,3) NULL COMMENT '分割产品重量 kg（白条分割成产品后总重 = Σ cut_out_in by white_bar_id，cutDone 落库）' AFTER acid_remove_loss,
    ADD COLUMN cut_loss           DECIMAL(12,3) NULL COMMENT '分割损耗 kg（= 出库重量 − 分割产品重量，cutDone 落库）' AFTER cut_product_weight;
