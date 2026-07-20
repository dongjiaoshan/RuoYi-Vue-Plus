-- FIX-BRD-CASTRATE-ISCASTRATED-001
-- 猪只主表新增「是否阉割」列：阉割选猪 picker 仅列未阉割的公仔猪 / 公育肥猪；
-- recordCastrate 提交成功后置 is_castrated=2（是），已阉割猪不再出现在选猪候选里。
ALTER TABLE t_farm_pig_info
    ADD COLUMN is_castrated TINYINT NOT NULL DEFAULT 1 COMMENT '是否阉割：1否 2是';
