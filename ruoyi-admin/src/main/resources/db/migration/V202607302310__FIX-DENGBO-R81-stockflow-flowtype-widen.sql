-- r81 盘点「异常」提交报 500：异常出库 flow_type='check_abnormal_out'（18 字符）超过 t_warehouse_stock_flow.flow_type
-- varchar(16) 上限 → MySQL Data truncation。计损 flow_type='check_out'（9 字符）能存，故计损正常、异常报错。
-- 放宽列宽到 32，容纳 check_abnormal_out 及后续更长的业务类型 code。非破坏性变更。
ALTER TABLE t_warehouse_stock_flow
    MODIFY COLUMN flow_type VARCHAR(32) NOT NULL COMMENT '业务类型 字典：pick=领用/return=退回/loss=损耗/produce=生产/check=盘点 等';
