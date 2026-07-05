-- row195 分割白条数：口径改为半只计 0.5、整只计 1（加权求和），值为小数。
-- t_warehouse_indicator_record.cut_bar_count 原 int 无法存 0.5 步进 → 改 DECIMAL(12,1)。
ALTER TABLE t_warehouse_indicator_record
    MODIFY COLUMN cut_bar_count DECIMAL(12,1) NULL COMMENT '分割白条数（当日转入分割车间白条数；半只0.5/整只1加权）';
