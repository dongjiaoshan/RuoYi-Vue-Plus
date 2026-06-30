-- r51（邓博）去批次：药品使用无批次说法。药品领用台账 batch_id 改可空，
-- 不带批次的领用/退回/损耗按 medicine_id 落账 + 扣药品库实时库存（真值在仓库 location_stock）。
ALTER TABLE t_breed_medicine_usage MODIFY COLUMN batch_id BIGINT NULL COMMENT '药品批次 ID（可选，去批次后可空，按 medicine_id 落账）';
