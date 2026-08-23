-- V6 row113：mp 白条出库卡「到场时间」显示 —— t_warehouse_bar_info.arrive_time 建表起从未被写过。
-- 称重入口（PigBurnRecordServiceImpl#weighBurn）现已回填 arrive_time；本脚本补齐已称重的存量白条。
-- 存量取 in_time（燎毛间称重/入库时刻）作到场时间，与外购猪只列表「到场时间 = 燎毛间称重完成时刻」同口径。
-- 只补 arrive_weight 已录（= 确实过完磅）且 arrive_time 仍空的行，未称重白条保持空（卡片显「—」即真实态）。
UPDATE t_warehouse_bar_info
   SET arrive_time = in_time
 WHERE del_flag = '0'
   AND arrive_time IS NULL
   AND arrive_weight IS NOT NULL
   AND in_time IS NOT NULL;
