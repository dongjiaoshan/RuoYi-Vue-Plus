-- DENGBO-R21/R24 采摘活动销售量按地块均分 + 录入完成结算（批次）
-- 背景：采摘去向=销售的量原先整笔堆进单条地块明细（row21 bug）；改为「录入完成」时把当前批次未结算销售量
-- 平均分摊到当前批次全部已采摘完成地块（3 位小数、尾差进最后一块）。批次用 settle_round 标记，支持
-- 「录入完成后新增地块单独核算」（row24 场景②）：新增地块与新增销售流水 settle_round=0，下一次录入完成单独结算。

-- 销售流水结算轮次：0=未结算(当前批次)，>0=已随第 N 次「录入完成」结算分摊
ALTER TABLE t_plant_plant_activity
    ADD COLUMN settle_round INT NOT NULL DEFAULT 0 COMMENT '销售量分摊结算轮次(0=未结算,>0=第N次录入完成已结算)';

-- 地块在采摘活动里的结算轮次：0=未结算(当前批次)，>0=已随第 N 次录入完成参与分摊
ALTER TABLE t_plant_plant_details
    ADD COLUMN pick_settle_round INT NOT NULL DEFAULT 0 COMMENT '采摘活动销售分摊结算轮次(0=未结算,>0=第N次录入完成已参与分摊)';
