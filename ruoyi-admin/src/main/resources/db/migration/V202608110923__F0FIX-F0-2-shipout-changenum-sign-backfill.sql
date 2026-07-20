-- F0FIX-F0-2（STOCK-D2-06 收尾）：ship_out 出库流水 change_num 符号规约回填
-- doc/11 §2.3 R9：change_num 带符号（正=入库 负=出库）。写入方 ShipmentServiceImpl.confirmCheck
-- 曾写正号，写侧已改 .negate()（与 cut_out 两写入方同规约，V202608110920 已回填 cut_out），
-- 此处把历史 ship_out 出库行符号翻正规约（change_quantity 保持绝对值不动）。
-- 读侧口径不受影响：库存总览回放用 inout_type + change_quantity（ship_out 单列「已发货」不计期末）；
-- 全仓读 change_num 的仅盘点 mapper（flow_type=check_in/check_out）与 mp 出入库记录 ABS(change_num)。
-- 幂等：翻号后 change_num <= 0，WHERE change_num > 0 不再命中。
UPDATE t_warehouse_stock_flow
   SET change_num = -change_num
 WHERE inout_type = 'OT'
   AND flow_type = 'ship_out'
   AND change_num > 0;
