-- PLT-STAT V1.1 生产指标及业务关联键（历史源单保持原值）。
ALTER TABLE t_warehouse_stock_flow
    ADD COLUMN crop_id BIGINT NULL COMMENT '自产采摘入库的作物',
    ADD COLUMN harvest_batch_id BIGINT NULL COMMENT '已知时关联仓库采摘批次 t_warehouse_planting_record.id';

ALTER TABLE t_plant_farm_records
    ADD COLUMN plant_detail_id BIGINT NULL COMMENT '本次实际推进的退茬明细 t_plant_plant_details.id';

CREATE TABLE t_plant_production_indicator_daily (
    id BIGINT NOT NULL COMMENT '主键',
    stat_date DATE NOT NULL COMMENT '统计日',
    planted_area DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '实际种植完成面积（亩或亩次）',
    planted_crop_count INT NOT NULL DEFAULT 0 COMMENT '种植品种数',
    planted_plot_count INT NOT NULL DEFAULT 0 COMMENT '种植地块数（块或茬次）',
    farm_work_count INT NOT NULL DEFAULT 0 COMMENT '有效农事作业条数',
    farm_work_area DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '有效农事作业面积（亩次）',
    harvest_finished_plot_count INT NOT NULL DEFAULT 0 COMMENT '实际完采地块数（块或茬次）',
    harvest_inbound_weight DECIMAL(18,3) NOT NULL DEFAULT 0 COMMENT '实际采摘入库重量（kg）',
    harvest_inbound_crop_count INT NOT NULL DEFAULT 0 COMMENT '实际采摘入库作物品种数',
    harvest_inbound_plot_count INT NOT NULL DEFAULT 0 COMMENT '实际采摘入库不重复地块数',
    harvest_crop_unmatched_count INT NOT NULL DEFAULT 0 COMMENT '无法确认作物的有效采摘入库流水数',
    rotation_plot_count INT NOT NULL DEFAULT 0 COMMENT '实际退茬地块数（块或茬次）',
    rotation_area DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '实际退茬面积（亩或亩次）',
    rotation_wait_days_sum INT NOT NULL DEFAULT 0 COMMENT '有效退茬等待天数总和',
    rotation_wait_valid_count INT NOT NULL DEFAULT 0 COMMENT '退茬等待天数有效样本数',
    rotation_unmatched_count INT NOT NULL DEFAULT 0 COMMENT '无法确认完采日期的退茬记录数',
    disaster_plot_count INT NOT NULL DEFAULT 0 COMMENT '当日受灾地块数',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '1001' COMMENT '租户编号',
    create_dept BIGINT NULL COMMENT '创建部门',
    create_by BIGINT NULL COMMENT '创建者',
    create_time DATETIME NULL COMMENT '创建时间',
    update_by BIGINT NULL COMMENT '更新者',
    update_time DATETIME NULL COMMENT '更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
    del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删唯一标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plant_prod_daily (tenant_id, stat_date, del_unique)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植生产指标日报';

CREATE TABLE t_plant_production_indicator_monthly (
    id BIGINT NOT NULL COMMENT '主键',
    stat_month DATE NOT NULL COMMENT '统计月首日',
    planted_area DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '实际种植完成面积（亩或亩次）',
    planted_crop_count INT NOT NULL DEFAULT 0 COMMENT '种植品种数',
    planted_plot_count INT NOT NULL DEFAULT 0 COMMENT '种植地块数（块或茬次）',
    plant_plan_count INT NOT NULL DEFAULT 0 COMMENT '本期计划明细数',
    plant_plan_completed_count INT NOT NULL DEFAULT 0 COMMENT '本期计划且本期完成的明细数',
    farm_work_count INT NOT NULL DEFAULT 0 COMMENT '有效农事作业条数',
    farm_work_area DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '有效农事作业面积（亩次）',
    harvest_finished_plot_count INT NOT NULL DEFAULT 0 COMMENT '实际完采地块数（块或茬次）',
    harvest_inbound_weight DECIMAL(18,3) NOT NULL DEFAULT 0 COMMENT '实际采摘入库重量（kg）',
    harvest_inbound_crop_count INT NOT NULL DEFAULT 0 COMMENT '实际采摘入库作物品种数',
    harvest_inbound_plot_count INT NOT NULL DEFAULT 0 COMMENT '实际采摘入库不重复地块数',
    harvest_crop_unmatched_count INT NOT NULL DEFAULT 0 COMMENT '无法确认作物的有效采摘入库流水数',
    rotation_plot_count INT NOT NULL DEFAULT 0 COMMENT '实际退茬地块数（块或茬次）',
    rotation_area DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '实际退茬面积（亩或亩次）',
    rotation_unmatched_count INT NOT NULL DEFAULT 0 COMMENT '无法确认完采日期的退茬记录数',
    harvest_performance_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '所属月已记录采摘绩效金额（元）',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '1001' COMMENT '租户编号',
    create_dept BIGINT NULL COMMENT '创建部门',
    create_by BIGINT NULL COMMENT '创建者',
    create_time DATETIME NULL COMMENT '创建时间',
    update_by BIGINT NULL COMMENT '更新者',
    update_time DATETIME NULL COMMENT '更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
    del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删唯一标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plant_prod_monthly (tenant_id, stat_month, del_unique)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植生产指标月报';

CREATE TABLE t_plant_production_indicator_annual (
    id BIGINT NOT NULL COMMENT '主键',
    stat_year INT NOT NULL COMMENT '统计年份',
    planted_area DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '实际种植完成面积（亩或亩次）',
    plant_plan_count INT NOT NULL DEFAULT 0 COMMENT '本期计划明细数',
    plant_plan_completed_count INT NOT NULL DEFAULT 0 COMMENT '本期计划且本期完成的明细数',
    harvest_inbound_weight DECIMAL(18,3) NOT NULL DEFAULT 0 COMMENT '实际采摘入库重量（kg）',
    disaster_record_count INT NOT NULL DEFAULT 0 COMMENT '灾害分地块登记记录数',
    rotation_plot_count INT NOT NULL DEFAULT 0 COMMENT '实际退茬地块数（块或茬次）',
    harvest_performance_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '所属月已记录采摘绩效金额（元）',
    performance_through_month CHAR(7) NULL COMMENT '年内最新已记录采摘绩效所属月 yyyy-MM',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '1001' COMMENT '租户编号',
    create_dept BIGINT NULL COMMENT '创建部门',
    create_by BIGINT NULL COMMENT '创建者',
    create_time DATETIME NULL COMMENT '创建时间',
    update_by BIGINT NULL COMMENT '更新者',
    update_time DATETIME NULL COMMENT '更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
    del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删唯一标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plant_prod_annual (tenant_id, stat_year, del_unique)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植生产指标年报';
