-- 蔬菜月台「自产产品收货」新增损耗重量（仓库-小程序 row21）。
-- 业务：某地块标记入库完成（is_finish=1）时，把当前剩余待入库重量记为损耗、待入库量清零（不再正数挂着）。
-- loss_weight 记在那条 is_finish=1 的收货行上；聚合「自产待入库」对已完成地块整体归 0、并把损耗暴露给列表卡。
ALTER TABLE t_warehouse_veg_receive
    ADD COLUMN loss_weight DECIMAL(10, 3) NULL DEFAULT 0 COMMENT '损耗重量(kg)：地块标记入库完成时把剩余待入库量结算为损耗' AFTER weight;
