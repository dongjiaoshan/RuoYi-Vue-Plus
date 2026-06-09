-- D-FIX-7 仓库板块真实化：放开 3 个与业务/代码设计不一致的 NOT NULL 约束。
-- 1. stock_flow.product_id：库存流水三维互斥（product_id / ear_no / plot_id）。自产果蔬入库走 plot 维度，
--    本无 product_id，原 NOT NULL 导致 VegReceiveServiceImpl.inbound 写流水失败（提交报错）。
-- 2/3. pig_burn_record.arrive_weight / loss_weight：到场重量是可选录入（mp 燎毛表单允许不填），
--    service 已设计 arriveWeight==null → lossWeight=null 分支，原 NOT NULL 与之矛盾，未填到场重量即提交失败。

ALTER TABLE t_warehouse_stock_flow
    MODIFY COLUMN product_id BIGINT NULL COMMENT '产品ID（product_id/ear_no/plot_id 三维互斥，plot 或耳标维度流水时为空）';

ALTER TABLE t_warehouse_pig_burn_record
    MODIFY COLUMN arrive_weight DECIMAL(12,3) NULL COMMENT '到场重量 kg（可选；填则计算损耗）';

ALTER TABLE t_warehouse_pig_burn_record
    MODIFY COLUMN loss_weight DECIMAL(12,3) NULL COMMENT '损耗 kg（到场重量 - 入库合计；到场重量未填时为空）';
