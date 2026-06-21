-- B(r14/r28) ADR-0017：母猪各类事件操作落库「操作当时日龄」冻结快照列 age_days。
-- 口径 = 事件日 - COALESCE(birth_date, introduce_date)，负值归 0；与各事件 VO 实时日龄、
-- PigAgeUtil.ageDaysAt 逐字对齐（ChronoUnit.DAYS.between(start,end) == DATEDIFF(end,start)）。
-- 9 张母猪生命周期事件表：配种/分娩/断奶/查情/返情空怀/死亡/淘汰/转移/出栏。

ALTER TABLE t_farm_pig_breeding  ADD COLUMN age_days INT NULL COMMENT '操作当时猪只日龄(事件日-出生日/引种日,落库冻结快照,ADR-0017)' AFTER ear_no;
ALTER TABLE t_farm_pig_farrow    ADD COLUMN age_days INT NULL COMMENT '操作当时猪只日龄(事件日-出生日/引种日,落库冻结快照,ADR-0017)' AFTER ear_no;
ALTER TABLE t_farm_pig_weaning   ADD COLUMN age_days INT NULL COMMENT '操作当时猪只日龄(事件日-出生日/引种日,落库冻结快照,ADR-0017)' AFTER ear_no;
ALTER TABLE t_farm_pig_heat      ADD COLUMN age_days INT NULL COMMENT '操作当时猪只日龄(事件日-出生日/引种日,落库冻结快照,ADR-0017)' AFTER ear_no;
ALTER TABLE t_farm_pig_abnormal  ADD COLUMN age_days INT NULL COMMENT '操作当时猪只日龄(事件日-出生日/引种日,落库冻结快照,ADR-0017)' AFTER ear_no;
ALTER TABLE t_farm_pig_death     ADD COLUMN age_days INT NULL COMMENT '操作当时猪只日龄(事件日-出生日/引种日,落库冻结快照,ADR-0017)' AFTER ear_no;
ALTER TABLE t_farm_pig_culling   ADD COLUMN age_days INT NULL COMMENT '操作当时猪只日龄(事件日-出生日/引种日,落库冻结快照,ADR-0017)' AFTER ear_no;
ALTER TABLE t_farm_pig_transfer  ADD COLUMN age_days INT NULL COMMENT '操作当时猪只日龄(事件日-出生日/引种日,落库冻结快照,ADR-0017)' AFTER ear_no;
ALTER TABLE t_farm_pig_marketing ADD COLUMN age_days INT NULL COMMENT '操作当时猪只日龄(事件日-出生日/引种日,落库冻结快照,ADR-0017)' AFTER ear_no;

-- 历史回填（只填 age_days IS NULL 的旧行；基准日缺失或事件日缺失则留 NULL）。
UPDATE t_farm_pig_breeding  e JOIN t_farm_pig_info p ON e.pig_id = p.id SET e.age_days = GREATEST(DATEDIFF(e.breeding_date,  COALESCE(p.birth_date, p.introduce_date)), 0) WHERE e.age_days IS NULL AND e.breeding_date  IS NOT NULL AND COALESCE(p.birth_date, p.introduce_date) IS NOT NULL;
UPDATE t_farm_pig_farrow    e JOIN t_farm_pig_info p ON e.pig_id = p.id SET e.age_days = GREATEST(DATEDIFF(e.farrow_date,    COALESCE(p.birth_date, p.introduce_date)), 0) WHERE e.age_days IS NULL AND e.farrow_date    IS NOT NULL AND COALESCE(p.birth_date, p.introduce_date) IS NOT NULL;
UPDATE t_farm_pig_weaning   e JOIN t_farm_pig_info p ON e.pig_id = p.id SET e.age_days = GREATEST(DATEDIFF(e.weaning_date,   COALESCE(p.birth_date, p.introduce_date)), 0) WHERE e.age_days IS NULL AND e.weaning_date   IS NOT NULL AND COALESCE(p.birth_date, p.introduce_date) IS NOT NULL;
UPDATE t_farm_pig_heat      e JOIN t_farm_pig_info p ON e.pig_id = p.id SET e.age_days = GREATEST(DATEDIFF(e.heat_date,      COALESCE(p.birth_date, p.introduce_date)), 0) WHERE e.age_days IS NULL AND e.heat_date      IS NOT NULL AND COALESCE(p.birth_date, p.introduce_date) IS NOT NULL;
UPDATE t_farm_pig_abnormal  e JOIN t_farm_pig_info p ON e.pig_id = p.id SET e.age_days = GREATEST(DATEDIFF(e.abnormal_date,  COALESCE(p.birth_date, p.introduce_date)), 0) WHERE e.age_days IS NULL AND e.abnormal_date  IS NOT NULL AND COALESCE(p.birth_date, p.introduce_date) IS NOT NULL;
UPDATE t_farm_pig_death     e JOIN t_farm_pig_info p ON e.pig_id = p.id SET e.age_days = GREATEST(DATEDIFF(e.death_date,     COALESCE(p.birth_date, p.introduce_date)), 0) WHERE e.age_days IS NULL AND e.death_date     IS NOT NULL AND COALESCE(p.birth_date, p.introduce_date) IS NOT NULL;
UPDATE t_farm_pig_culling   e JOIN t_farm_pig_info p ON e.pig_id = p.id SET e.age_days = GREATEST(DATEDIFF(e.culling_date,   COALESCE(p.birth_date, p.introduce_date)), 0) WHERE e.age_days IS NULL AND e.culling_date   IS NOT NULL AND COALESCE(p.birth_date, p.introduce_date) IS NOT NULL;
UPDATE t_farm_pig_transfer  e JOIN t_farm_pig_info p ON e.pig_id = p.id SET e.age_days = GREATEST(DATEDIFF(e.transfer_date,  COALESCE(p.birth_date, p.introduce_date)), 0) WHERE e.age_days IS NULL AND e.transfer_date  IS NOT NULL AND COALESCE(p.birth_date, p.introduce_date) IS NOT NULL;
UPDATE t_farm_pig_marketing e JOIN t_farm_pig_info p ON e.pig_id = p.id SET e.age_days = GREATEST(DATEDIFF(e.marketing_date, COALESCE(p.birth_date, p.introduce_date)), 0) WHERE e.age_days IS NULL AND e.marketing_date IS NOT NULL AND COALESCE(p.birth_date, p.introduce_date) IS NOT NULL;
