-- 采摘活动管理「采摘重量录入」weight 落字段（FIX-PLT-MP-HARVEST-001，#3=a）。
-- 采收 tab 去重量后，采摘重量改在农事「采摘活动管理」录：submitHarvestWeight 累加 plant_details.actual_yield
-- 并写一行 t_plant_farm_records（farm_type=harvest_activity）携带本次 harvest_weight，供记录 tab 展示「作物 + 重量kg」。
ALTER TABLE t_plant_farm_records
    ADD COLUMN harvest_weight DECIMAL(12,3) NULL COMMENT '采摘重量 kg（farm_type=harvest_activity 采摘活动管理录入）' AFTER loss_yield;
