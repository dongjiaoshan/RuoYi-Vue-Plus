-- FIX-HEAT-001 #39a：放宽 t_farm_pig_heat.heat_result NOT NULL → NULL。
-- 602-5 后 mp 查情不配种只记发情情况 + 不配种原因，不再下发 heat_result；
-- 原 NOT NULL 约束导致 mp 提交时 insert 触发 Column 'heat_result' cannot be null（接口 500）。
-- 仅放宽既有列可空性，不动其它列、不删数据。
ALTER TABLE t_farm_pig_heat
    MODIFY heat_result VARCHAR(16) NULL COMMENT '查情结果（602-5 后 mp 不录，可空）';
