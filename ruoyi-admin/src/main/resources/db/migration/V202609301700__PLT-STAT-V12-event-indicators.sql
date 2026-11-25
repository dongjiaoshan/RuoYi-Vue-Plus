ALTER TABLE t_plant_production_indicator_daily
    ADD COLUMN harvest_performance_per_person_estimate DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '日人均采摘绩效估算（按当前单价及称重绩效百分比，元/人）',
    ADD COLUMN harvest_dock_unmatched_count INT NOT NULL DEFAULT 0 COMMENT '日月台实收无法完全判定首入库来源的流水数';

ALTER TABLE t_plant_production_indicator_monthly
    ADD COLUMN yield_completion_percent DECIMAL(18,2) NULL DEFAULT NULL COMMENT '当月有实产的完采茬次产量达成率（%）',
    ADD COLUMN rotation_average_days DECIMAL(12,1) NOT NULL DEFAULT 0 COMMENT '月平均退茬周期（天）',
    ADD COLUMN disaster_plot_count INT NOT NULL DEFAULT 0 COMMENT '月受灾去重地块数',
    ADD COLUMN harvest_performance_per_person DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '月人均采摘绩效（元/人）',
    ADD COLUMN disaster_loss_area DECIMAL(16,3) NOT NULL DEFAULT 0 COMMENT '月灾损面积估算（亩，使用登记的产量损失率）',
    ADD COLUMN disaster_loss_yield DECIMAL(16,3) NOT NULL DEFAULT 0 COMMENT '月登记灾损产量（kg）',
    ADD COLUMN harvest_dock_unmatched_count INT NOT NULL DEFAULT 0 COMMENT '月台实收无法完全判定首入库来源的流水数（月）';

ALTER TABLE t_plant_production_indicator_annual
    ADD COLUMN rotation_average_days DECIMAL(12,1) NOT NULL DEFAULT 0 COMMENT '年平均退茬周期（天）',
    ADD COLUMN harvest_performance_per_person DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '年人均采摘绩效（元/人）',
    ADD COLUMN harvest_dock_unmatched_count INT NOT NULL DEFAULT 0 COMMENT '月台实收无法完全判定首入库来源的流水数（年）';
