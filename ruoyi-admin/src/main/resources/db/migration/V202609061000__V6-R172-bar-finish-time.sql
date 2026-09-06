-- 白条「处理完成时间」专用锚（V6 row172）。
--
-- 为什么要单开一列：日指标现在按 DATE(in_time) 分桶，而 in_time 在称重、每次产品逐项入库、
-- 处理完成三处被反复覆写，终值才是「处理完成」。后果是同一头猪可能落进不同日期的桶，
-- 且事后重跑结果与当初落库的快照对不上（staging 实测 07-16：快照屠宰率 304.016%，
-- 今天重跑同一天得 152.766%）。finish_time 只在 finishBurn 那一次状态推进里写，
-- 且 status 守卫保证写得进一次，之后不变。
ALTER TABLE t_warehouse_bar_info
    ADD COLUMN finish_time DATETIME NULL COMMENT '处理完成时间（finishBurn 写入，只写一次；日指标按此分桶）' AFTER in_time;

-- 存量回填：白条推进到 in_stock 之后，两条推进 UPDATE 的 status 守卫全部失效，
-- 所以对「已完成处理」（in_weight 非空）的行，当前 in_time 就是真实的处理完成时刻——
-- 这一次回填是精确值，不是近似。未完成处理的行留 NULL。
UPDATE t_warehouse_bar_info
   SET finish_time = in_time
 WHERE del_flag = '0'
   AND in_weight IS NOT NULL
   AND in_time IS NOT NULL
   AND finish_time IS NULL;
