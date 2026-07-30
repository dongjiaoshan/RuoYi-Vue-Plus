-- 分娩记录的关联配种 id 允许为空。
--
-- 初始数据导入的母猪只带 last_mating_date，没有系统内的 BREED 事件，t_farm_pig_info.mating_id 与
-- t_farm_pig_breeding 都是空的；breeding_id NOT NULL 且无 DEFAULT 时，STRICT_TRANS_TABLES 下
-- INSERT 直接报 1364，mp「分娩录入」提交全部失败。分娩本身不依赖配种记录，该外键只是可选溯源线索。
-- 与 t_farm_pig_weaning.breeding_id（本就 NULL-able）口径对齐。
ALTER TABLE t_farm_pig_farrow
    MODIFY COLUMN breeding_id BIGINT NULL COMMENT '关联配种记录 id（无系统内配种记录时为空）';
