-- D-0227：只在业务当天采集，不初始化历史快照。
CREATE TABLE t_plant_plot_snapshot_day (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '1001' COMMENT '租户编号',
    snap_date DATE NOT NULL COMMENT '收工业务日',
    create_dept BIGINT NULL COMMENT '创建部门',
    create_by BIGINT NULL COMMENT '创建者',
    create_time DATETIME NULL COMMENT '创建时间',
    update_by BIGINT NULL COMMENT '更新者',
    update_time DATETIME NULL COMMENT '最近一次采集时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
    del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删唯一标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plant_snapshot_day (tenant_id, snap_date, del_unique)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植地块收工采集记录（含有效地块为零的业务日）';

CREATE TABLE t_plant_plot_status_daily (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '1001' COMMENT '租户编号',
    snap_date DATE NOT NULL COMMENT '收工业务日',
    plot_id BIGINT NOT NULL COMMENT '地块 ID',
    zone_id BIGINT NOT NULL COMMENT '采集时所属片区 ID',
    zone_enabled TINYINT NOT NULL COMMENT '片区是否启用',
    plot_valid TINYINT NOT NULL COMMENT '地块是否有效',
    plot_area DECIMAL(10,2) NOT NULL COMMENT '采集时地块面积（亩）',
    is_idle TINYINT NOT NULL COMMENT '是否空闲',
    is_in_field TINYINT NOT NULL COMMENT '是否有未退茬且未完成采摘的在田茬',
    is_pending_rotation TINYINT NOT NULL COMMENT '是否有已完采未退茬的茬',
    is_overdue TINYINT NOT NULL COMMENT '是否有超过最晚采摘日的在田茬',
    in_field_crop_ids JSON NOT NULL COMMENT '各在田品种 ID 的去重数组',
    create_dept BIGINT NULL COMMENT '创建部门',
    create_by BIGINT NULL COMMENT '创建者',
    create_time DATETIME NULL COMMENT '创建时间',
    update_by BIGINT NULL COMMENT '更新者',
    update_time DATETIME NULL COMMENT '更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
    del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删唯一标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plant_plot_status_daily (tenant_id, snap_date, del_unique, plot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='启用片区内有效地块的收工状态快照';

ALTER TABLE t_plant_production_indicator_daily
    ADD COLUMN idle_plot_count INT NULL COMMENT '收工空闲地块数',
    ADD COLUMN idle_area DECIMAL(18,2) NULL COMMENT '收工空闲面积（亩）',
    ADD COLUMN in_field_area DECIMAL(18,2) NULL COMMENT '收工在田面积（亩）',
    ADD COLUMN in_field_crop_count INT NULL COMMENT '收工在田品种去重数',
    ADD COLUMN in_field_plot_count INT NULL COMMENT '收工在田地块数',
    ADD COLUMN total_plot_count INT NULL COMMENT '收工有效地块数',
    ADD COLUMN overdue_harvest_plot_count INT NULL COMMENT '收工超期采摘未完成地块数',
    ADD COLUMN pending_rotation_plot_count INT NULL COMMENT '收工待退茬地块数';

ALTER TABLE t_plant_production_indicator_monthly
    ADD COLUMN idle_plot_count INT NULL COMMENT '月末空闲地块数',
    ADD COLUMN plot_utilization_percent DECIMAL(5,2) NULL COMMENT '月末地块利用率（%）',
    ADD COLUMN overdue_harvest_plot_count INT NULL COMMENT '月末超期采摘未完成地块数',
    ADD COLUMN in_field_area DECIMAL(18,2) NULL COMMENT '月末在田面积（亩）',
    ADD COLUMN in_field_plot_count INT NULL COMMENT '月末在田地块数',
    ADD COLUMN in_field_crop_count INT NULL COMMENT '月末在田品种去重数',
    ADD COLUMN idle_area DECIMAL(18,2) NULL COMMENT '月末空闲面积（亩）',
    ADD COLUMN pending_rotation_plot_count INT NULL COMMENT '月末待退茬地块数';

ALTER TABLE t_plant_production_indicator_annual
    ADD COLUMN average_plantings_per_plot DECIMAL(18,2) NULL COMMENT '年完成种植次数除以年末有效地块数';
