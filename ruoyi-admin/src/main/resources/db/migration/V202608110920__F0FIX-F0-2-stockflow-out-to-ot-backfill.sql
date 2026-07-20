-- F0FIX-F0-2（STOCK-D2-02）：stock_flow 方向码双码回填 'OUT' → 'OT' + cut_out 出库符号规约回填
-- 写侧根因：ProductProductionServiceImpl.writeWhiteBarOutFlow 曾用 'OUT'（其余写入方全部 'OT'），
-- 所有聚合读取端（库存总览回放 / 库位卡今日出库 / 仓库看板 / mp 出入库记录）只认 IN/'OT'，
-- 'OUT' 行两边都不落 → 白条出库在报表中隐形。写侧常量已改 'OT'，此处回填历史行。
-- doc/11 §2.3 R10：inout_type 码值仅 IN/OT。幂等：跑过一次后无 inout_type='OUT' 行，重跑 0 affected。
UPDATE t_warehouse_stock_flow
   SET inout_type = 'OT'
 WHERE inout_type = 'OUT';

-- doc/11 §2.3 R9：change_num 带符号（正=入库 负=出库）。cut_out 两个写入方
-- （PigCutRecordServiceImpl / ProductProductionServiceImpl）曾写正号，写侧已改 .negate()，
-- 此处把历史 cut_out 出库行符号翻正规约（change_quantity 保持绝对值不动）。
-- 读侧口径不受影响：聚合端全部用 inout_type + change_quantity，不用 change_num 求和。
-- ship_out 正号行不在本次回填（写入方 ShipmentServiceImpl 修复另批，写读同步收口）。
-- 幂等：翻号后 change_num <= 0，WHERE change_num > 0 不再命中。
UPDATE t_warehouse_stock_flow
   SET change_num = -change_num
 WHERE inout_type = 'OT'
   AND flow_type = 'cut_out'
   AND change_num > 0;
