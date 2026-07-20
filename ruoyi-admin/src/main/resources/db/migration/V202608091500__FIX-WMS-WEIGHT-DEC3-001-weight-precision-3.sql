-- FIX-WMS-WEIGHT-DEC3-001：仓库/门店重量类列精度 decimal(12,2) → decimal(12,3)
-- 承载 kg 重量的列存 2 位小数，但 mp 端按 3 位（formatKg toFixed(3)）展示，第 3 位在写库时被截断/四舍五入
-- （0.701→0.70 显示 0.700；0.349→0.35 显示 0.350）。后端写入本就传满精度值（无 setScale(2)），仅列精度截断。
-- 加宽为 3 位后新写入即保留第 3 位小数；历史已截断数据无法恢复（源头未存第 3 位）。
ALTER TABLE t_store_return
    MODIFY COLUMN goods_weight    DECIMAL(12,3) NULL COMMENT '报退货物重量(kg)（原型「货物重量」）',
    MODIFY COLUMN received_weight DECIMAL(12,3) NULL COMMENT '仓库实收重量(kg)（仓库确认时填）';

ALTER TABLE t_warehouse_stock_flow
    MODIFY COLUMN change_num      DECIMAL(12,3) NOT NULL COMMENT '变更数量（正负，正=入 负=出）',
    MODIFY COLUMN change_quantity DECIMAL(12,3) NULL COMMENT '变更数量绝对值（前端展示用，后端自动算）';

ALTER TABLE t_warehouse_demand_manage
    MODIFY COLUMN expected_weight DECIMAL(12,3) NULL COMMENT '预计到店重量(kg)';
