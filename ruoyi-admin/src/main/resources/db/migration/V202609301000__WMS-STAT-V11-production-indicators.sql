-- D-0206/0207/0208/0212 仓库生产指标 V1.1。新增列自每日跑批和历史回刷填充。
ALTER TABLE t_warehouse_indicator_record
    MODIFY COLUMN cut_bar_count DECIMAL(12,1) NULL COMMENT '按领用日统计分割扇数（半扇1、整只2）',
    MODIFY COLUMN cut_loss DECIMAL(12,3) NULL DEFAULT 0 COMMENT '分割损耗重（按对应分割单领用日归集，kg）',
    ADD COLUMN slaughter_ytd_received_count INT NULL COMMENT '年初至报表日首次接收头数',
    ADD COLUMN slaughter_ytd_completed_count INT NULL COMMENT '同批猪截至报表日完成头数',
    ADD COLUMN slaughter_completion_rate DECIMAL(7,2) NULL COMMENT '屠宰数据完成率（%，分母0返回0）',
    ADD COLUMN cut_pending_half_count INT NULL COMMENT '日末已领用未完成分割扇数',
    ADD COLUMN cut_loss_rate DECIMAL(16,2) NULL COMMENT '同批分割损耗重除以领用重（%，分母0为NULL）',
    ADD COLUMN veg_raw_inbound_weight DECIMAL(16,3) NULL COMMENT '果蔬毛菜实际入库重量（kg）',
    ADD COLUMN veg_delivery_out_weight DECIMAL(16,3) NULL COMMENT '果蔬实际配送离库重量（kg）',
    ADD COLUMN veg_produced_portion_count INT NULL COMMENT '有效果蔬生产份数';

ALTER TABLE t_warehouse_monthly_record
    ADD COLUMN source_marketing_count INT NULL COMMENT '按源出栏日归月的出栏头数',
    ADD COLUMN source_marketing_weight DECIMAL(16,3) NULL COMMENT '同批源出栏总重（kg）',
    ADD COLUMN source_marketing_avg_weight DECIMAL(16,3) NULL COMMENT '源出栏总重除以同批头数（kg）',
    ADD COLUMN received_bar_weight DECIMAL(16,3) NULL COMMENT '同月接收批次白条总重（kg）',
    ADD COLUMN received_bar_pig_count INT NULL COMMENT '同月接收整猪头数',
    ADD COLUMN received_bar_avg_weight DECIMAL(16,3) NULL COMMENT '同月接收批次白条均重（kg）',
    ADD COLUMN sum_daily_bar_yield_rate DECIMAL(16,3) NULL COMMENT '小程序矩阵口径日白条出品率之和',
    ADD COLUMN positive_bar_yield_days INT NULL COMMENT '小程序矩阵口径正值日出品率天数',
    ADD COLUMN matrix_bar_yield_rate DECIMAL(16,2) NULL COMMENT '小程序日矩阵同口径月白条出品率（%）',
    ADD COLUMN cut_bar_count DECIMAL(16,1) NULL COMMENT '按领用日归月的分割扇数',
    ADD COLUMN cut_loss DECIMAL(16,3) NULL COMMENT '按分割单领用日归月的分割损耗重（kg）',
    ADD COLUMN cut_loss_pickup_weight DECIMAL(16,3) NULL COMMENT '月分割损耗率分母：同批分割领用总重（kg）',
    ADD COLUMN cut_loss_rate DECIMAL(16,2) NULL COMMENT '分割损耗率（%，分母0为NULL）',
    ADD COLUMN veg_production_pick_weight DECIMAL(16,3) NULL COMMENT '果蔬生产毛领用总重（kg）',
    ADD COLUMN veg_raw_inbound_weight DECIMAL(16,3) NULL COMMENT '果蔬毛菜实际入库重量（kg）',
    ADD COLUMN veg_delivery_out_weight DECIMAL(16,3) NULL COMMENT '果蔬实际配送离库重量（kg）',
    ADD COLUMN veg_produced_portion_count INT NULL COMMENT '有效果蔬生产份数';
