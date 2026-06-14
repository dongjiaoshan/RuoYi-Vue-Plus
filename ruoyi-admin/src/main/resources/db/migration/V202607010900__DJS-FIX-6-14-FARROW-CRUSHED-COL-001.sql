-- DJS-FIX-6-14-FARROW-CRUSHED-COL-001（测试 row212）：分娩记录补「压死数」列。
-- 客户原型 93「分娩仔猪情况」+ 母猪详情产仔性能图要求「压死」独立系列，
-- 原 t_farm_pig_farrow 只有 deformed_born（畸形）无独立「压死」列，导致产仔图无法拆出压死系列。
-- 纯 ADD COLUMN，向后兼容（旧记录 NULL DEFAULT 0）；录入表单后续补采集前该列恒 0（数据缺口已在 reports 标注）。

ALTER TABLE t_farm_pig_farrow
  ADD COLUMN crushed_born INT NULL DEFAULT 0 COMMENT '压死数（原型 93 产仔性能·压死系列）' AFTER deformed_born;
