-- V6 row113 补正：上一支回填（V202608310500）用 bar_info.in_time 当到场时间，而 in_time 的最终值是
-- 「处理完成」那一刻，天然晚于各产出行的入库时刻 → 存量里出现「到场时间晚于入库时间」的倒挂
-- （staging 实测 5 条，最大偏 133 秒）。倒挂的行把到场时间拉回该白条最早一条产出行的入库时刻，
-- 那是这批数据里能拿到的、最接近真实到场的时点，且保证不晚于任何一行的入库时间。
-- 只动「回填出来的」行（arrive_time = in_time 是回填签名）且确实倒挂的行；称重入口新写的值不碰。
UPDATE t_warehouse_bar_info b
   JOIN (SELECT white_bar_id, MIN(produce_time) AS first_in
           FROM t_warehouse_product_inhouse
          WHERE del_flag = '0'
            AND white_bar_id IS NOT NULL
            AND produce_time IS NOT NULL
          GROUP BY white_bar_id) x
     ON x.white_bar_id = b.id
    SET b.arrive_time = x.first_in
  WHERE b.del_flag = '0'
    AND b.arrive_time IS NOT NULL
    AND b.arrive_time = b.in_time
    AND b.arrive_time > x.first_in;
