ALTER TABLE t_store_production_indicator_daily
    ADD COLUMN order_timely TINYINT NULL COMMENT '需求日是否及时：1 是、0 否、NULL 无可判定提交',
    ADD COLUMN order_placed TINYINT NOT NULL DEFAULT 0 COMMENT '当日是否发生首次提交，用于年有效天数',
    ADD COLUMN pork_received_weight_kg DECIMAL(18,3) NOT NULL DEFAULT 0 COMMENT '猪肉接收重量 kg，白条加分割成品',
    ADD COLUMN veg_damage_portion_count DECIMAL(18,3) NOT NULL DEFAULT 0 COMMENT '按损坏登记日统计的果蔬损坏份数',
    ADD COLUMN veg_damage_received_portion_count DECIMAL(18,3) NOT NULL DEFAULT 0 COMMENT '按收货日归集的果蔬损坏份数',
    ADD COLUMN split_yield_rate DECIMAL(18,2) NULL COMMENT '日分割出品率百分数，无白条到店为 NULL',
    ADD COLUMN split_loss_rate DECIMAL(18,2) NULL COMMENT '日分割损耗率百分数，无白条到店为 NULL';
