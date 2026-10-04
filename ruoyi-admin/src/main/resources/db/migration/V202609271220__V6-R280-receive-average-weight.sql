-- V6-R280 / D-0137：接收均重 = 日表接收重量 / 当日燎毛记录耳号去重数。
-- 版本取自目标 staging flyway max 202609271120 + 1h；不按本机日期取号。
-- 新分母按 burn_time 自然日，与旧 arrive_weight 的 arrive_time cohort 各自保持甲方口径。
ALTER TABLE t_warehouse_indicator_record
    ADD COLUMN arrive_pig_count INT NOT NULL DEFAULT 0 COMMENT '接收均重分母：当日燎毛记录耳号去重数' AFTER arrive_weight,
    ADD COLUMN avg_arrive_weight DECIMAL(12,3) NULL COMMENT '接收均重：日接收重量/当天燎毛记录去重耳号数；分母0为NULL' AFTER arrive_pig_count;

-- 历史只回填新两列；已有日表接收重量、其它日指标和月表一律不重算。
-- DISTINCT ear_no 按 SQL 语义排除NULL，不以burn_id或其它标识替代耳号。
UPDATE t_warehouse_indicator_record d
LEFT JOIN (
    SELECT tenant_id, DATE(burn_time) AS stat_date, COUNT(DISTINCT ear_no) AS pig_count
    FROM t_warehouse_pig_burn_record
    WHERE del_flag = '0'
    GROUP BY tenant_id, DATE(burn_time)
) b ON b.tenant_id = d.tenant_id AND b.stat_date = d.stat_date
SET d.arrive_pig_count = COALESCE(b.pig_count, 0),
    d.avg_arrive_weight = ROUND(d.arrive_weight / NULLIF(COALESCE(b.pig_count, 0), 0), 3)
WHERE d.del_flag = '0';
