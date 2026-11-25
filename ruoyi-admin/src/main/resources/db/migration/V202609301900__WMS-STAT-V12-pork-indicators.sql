-- D-0226 仓库猪肉指标，历史值由 warehouse-stat 回刷。
ALTER TABLE t_warehouse_indicator_record
    ADD COLUMN slaughter_pending_count INT NULL COMMENT '日末累计已接收未完成燎毛头数',
    ADD COLUMN cut_completion_rate DECIMAL(7,2) NULL COMMENT '当日领用白条截至日末的分割完成率（%）',
    ADD COLUMN cut_output_unmatched_count INT NULL COMMENT '按产出业务日统计的未配对分割产出流水数';

ALTER TABLE t_warehouse_monthly_record
    ADD COLUMN cut_bar_weight DECIMAL(16,3) NULL COMMENT '按领用月归集的分割白条总重（kg）',
    ADD COLUMN cut_product_weight DECIMAL(16,3) NULL COMMENT '按领用月归集的分割产出总重（kg）',
    ADD COLUMN bar_yield_rate_sum DECIMAL(16,2) NULL COMMENT '当月所有可比日白条出品率之和（含0%）',
    ADD COLUMN bar_yield_rate_days INT NULL COMMENT '当月所有可比日白条出品率天数（含0%）';
