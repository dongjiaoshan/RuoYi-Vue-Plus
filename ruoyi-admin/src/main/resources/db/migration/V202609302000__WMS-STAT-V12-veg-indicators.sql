-- D-0227：收工余额按库位和原账单位留存，location_id=0 为当日已采集标记。
CREATE TABLE t_warehouse_veg_stock_snapshot (
    id BIGINT NOT NULL COMMENT '主键（雪花）',
    stat_date DATE NOT NULL COMMENT '收工业务日',
    location_id BIGINT NOT NULL COMMENT '库位 ID，0 为已采集标记',
    stock_unit VARCHAR(16) NOT NULL COMMENT '库存原账单位，公斤归一为 kg',
    stock_quantity DECIMAL(18,3) NOT NULL COMMENT '该库位该单位的果蔬原料账面余额',
    stock_weight DECIMAL(21,6) NOT NULL COMMENT '原账 kg 和可可靠折重的余额（kg）',
    unconverted_count DECIMAL(18,3) NOT NULL COMMENT '未折重的非 kg 余额',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '1001' COMMENT '租户编号',
    create_dept BIGINT NULL COMMENT '创建部门',
    create_by BIGINT NULL COMMENT '创建人',
    create_time DATETIME NULL COMMENT '采集时间',
    update_by BIGINT NULL COMMENT '更新人',
    update_time DATETIME NULL COMMENT '更新时间',
    remark VARCHAR(500) NULL COMMENT '备注',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
    del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删唯一标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_veg_stock_day (tenant_id, stat_date, location_id, stock_unit, del_unique)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库果蔬原料日末库存快照';

ALTER TABLE t_warehouse_indicator_record
    ADD COLUMN veg_standard_portion_count INT NULL COMMENT '有正标准重的生产件数',
    ADD COLUMN veg_overspec_portion_count INT NULL COMMENT '实称超过标准重 103% 的生产件数',
    ADD COLUMN veg_overspec_percent_sum DECIMAL(32,12) NULL COMMENT '各超规件超出百分比之和',
    ADD COLUMN veg_overspec_rate DECIMAL(12,2) NULL COMMENT '果蔬生产次数超规率（%）',
    ADD COLUMN veg_avg_overspec_percent DECIMAL(20,2) NULL COMMENT '超规件平均超出百分比（%）',
    ADD COLUMN veg_spec_qualified_count INT NULL COMMENT '实称在标准重至 103% 内的合格件数',
    ADD COLUMN veg_undamaged_qualified_count INT NULL COMMENT '重量合格且未标损坏的件数',
    ADD COLUMN veg_damaged_portion_count INT NULL COMMENT '按原生产日归集的果蔬损坏份数',
    ADD COLUMN veg_demand_portion_count DECIMAL(18,3) NULL COMMENT '可换算为份的有效果蔬需求量',
    ADD COLUMN veg_demand_fulfillment_rate DECIMAL(5,2) NULL COMMENT '果蔬产品需求满足率（%）',
    ADD COLUMN veg_demand_kg_excluded_count INT NULL COMMENT '无原始份数而排除的公斤需求条数',
    ADD COLUMN veg_stock_weight DECIMAL(18,3) NULL COMMENT '日末果蔬原料库存（kg），未拍快照为空',
    ADD COLUMN veg_stock_unconverted_count DECIMAL(18,3) NULL COMMENT '日末无可靠折重的果蔬原料数量，未拍快照为空',
    ADD COLUMN veg_prod_return_weight DECIMAL(18,3) NULL COMMENT '果蔬生产退回重量（kg）',
    ADD COLUMN veg_pack_usage_weight DECIMAL(18,3) NULL COMMENT '果蔬打包耗料重量（kg）';

ALTER TABLE t_warehouse_monthly_record
    ADD COLUMN veg_demand_fulfillment_rate DECIMAL(5,2) NULL COMMENT '有可比需求的各日满足率的平均（%）',
    ADD COLUMN veg_demand_kg_excluded_count INT NULL COMMENT '无原始份数而排除的公斤需求条数',
    ADD COLUMN veg_pack_loss_rate DECIMAL(20,2) NULL COMMENT '月果蔬打包损耗率（%）',
    ADD COLUMN veg_damaged_portion_count INT NULL COMMENT '按原生产月归集的果蔬损坏份数',
    ADD COLUMN veg_damage_rate DECIMAL(5,2) NULL COMMENT '月果蔬损坏率（%）',
    ADD COLUMN veg_production_loss_weight DECIMAL(18,3) NULL COMMENT '月内逐日领用损耗之和（kg）',
    ADD COLUMN veg_production_loss_rate DECIMAL(20,2) NULL COMMENT '月果蔬生产损耗率（%）',
    ADD COLUMN veg_spec_qualified_rate DECIMAL(5,2) NULL COMMENT '月果蔬生产规格合格率（%）',
    ADD COLUMN veg_avg_overspec_percent DECIMAL(20,2) NULL COMMENT '当月超规件平均超出百分比（%）',
    ADD COLUMN veg_end_stock_weight DECIMAL(18,3) NULL COMMENT '月末果蔬原料库存（kg），无快照为空',
    ADD COLUMN veg_end_stock_date DATE NULL COMMENT '期末库存所用快照日期',
    ADD COLUMN veg_undamaged_qualified_count INT NULL COMMENT '月内重量合格且未标损坏的份数';
