-- ============================================================
-- D02 closing patch — 65 张 D01 SYS-INIT-001 业务表审计字段对齐 ruoyi BaseEntity
--   一次性 ALTER 62 张表（DB 实际存在 + 还是 varchar 的）：
--     MODIFY create_by  BIGINT NULL
--     MODIFY update_by  BIGINT NULL（若存在 varchar）
--     ADD    create_dept BIGINT NULL（若不存在）
--   源 DDL V202605200900~V202605200904 已同步用 BIGINT + create_dept；本 patch 让运行库追上。
--   先 UPDATE 字符串 audit 列为 NULL，避免 'system' 字符串 NumberFormatException
-- ============================================================

UPDATE sys_farm SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE sys_farm SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE sys_farm
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_breed_medicine_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_breed_medicine_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_breed_medicine_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_breed_medicine_use SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_breed_medicine_use SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_breed_medicine_use
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_breed_production_config SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_breed_production_config SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_breed_production_config
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_barn_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_barn_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_barn_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_barn_pen SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_barn_pen SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_barn_pen
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_breed_config SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_breed_config SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_breed_config
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_breed_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_breed_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_breed_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_castrate_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_castrate_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_castrate_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_grow_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_grow_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_grow_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_medicine_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_medicine_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_medicine_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_abnormal SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_abnormal SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_abnormal
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_breeding SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_breeding SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_breeding
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_culling SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_culling SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_culling
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_death SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_death SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_death
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_farrow SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_farrow SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_farrow
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_heat SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_heat SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_heat
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_introduce SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_introduce SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_introduce
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_marketing SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_marketing SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_marketing
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_pigletno SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_pigletno SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_pigletno
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_transfer SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_transfer SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_transfer
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_weaning SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_weaning SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_weaning
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_status_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
ALTER TABLE t_farm_status_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_wean_weight SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_wean_weight SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_wean_weight
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_md_store SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_md_store SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_md_store
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_md_supplier SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_md_supplier SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_md_supplier
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_crop_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_crop_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_crop_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_crop_organic SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_crop_organic SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_crop_organic
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_farm_records SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_farm_records SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_farm_records
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_organic_plotno SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
ALTER TABLE t_plant_organic_plotno
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_pick_activity SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_pick_activity SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_pick_activity
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_plant_details SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_plant_details SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_plant_details
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_plant_plan SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_plant_plan SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_plant_plan
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_plot_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_plot_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_plot_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_plot_organic SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_plot_organic SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_plot_organic
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_plot_zone SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_plot_zone SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_plot_zone
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_work_people SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_work_people SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_work_people
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_work_performance SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_work_performance SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_work_performance
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_work_team SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_work_team SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_work_team
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_zone_plotno SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
ALTER TABLE t_plant_zone_plotno
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_store_check_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_store_check_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_store_check_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_store_member SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_store_member SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_store_member
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_store_member_consumption SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_store_member_consumption SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_store_member_consumption
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_store_product_relation SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_store_product_relation SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_store_product_relation
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_store_return SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_store_return SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_store_return
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_store_sale_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_store_sale_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_store_sale_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_bar_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_bar_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_bar_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_check_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_check_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_check_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_demand_manage SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_demand_manage SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_demand_manage
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_handle_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_handle_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_handle_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_location_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_location_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_location_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_location_stock SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_location_stock SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_location_stock
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_planting_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_planting_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_planting_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_product_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_product_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_product_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_product_produce SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_product_produce SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_product_produce
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_product_production SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_product_production SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_product_production
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_return_product SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_return_product SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_return_product
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_stock_flow SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_stock_flow SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_stock_flow
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_supplier_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_supplier_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_supplier_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_trace_code SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_trace_code SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_trace_code
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_vegetable_handle SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_vegetable_handle SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_vegetable_handle
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

-- sys_farm seed: create_by 'system' → 1 (idempotent — UPDATE 仅当当前值是 'system' 才生效)
UPDATE sys_farm SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '';
UPDATE sys_farm SET create_by = 1 WHERE id = 1001 AND (create_by IS NULL OR create_by = 0);
