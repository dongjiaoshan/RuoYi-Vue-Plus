-- DJS-FIX-6-14-FARROW-MALEFEMALE-BACKFILL-001（测试 row217-②）：回填分娩记录公/母头数。
-- mp 分娩录入此前只下发健仔/弱仔留养的公母细分（healthy_male/healthy_female/weak_raised_male/weak_raised_female），
-- 未计算汇总的 male_count/female_count，导致旧分娩窝 male_count/female_count 为 NULL/0，
-- 仔猪耳号标记页选窝卡「公猪 N 头 / 母猪 N 头」恒显 0。
-- FarrowServiceImpl.recordFarrow 已改为「未显式传则由健仔公+弱留公 / 健仔母+弱留母 派生」，仅作用于今后新建；
-- 本脚本回填历史行（male_count IS NULL OR male_count=0）：活产公/母 = 健仔公+弱留公 / 健仔母+弱留母。

UPDATE t_farm_pig_farrow
SET male_count = COALESCE(healthy_male, 0) + COALESCE(weak_raised_male, 0)
WHERE (male_count IS NULL OR male_count = 0)
  AND (COALESCE(healthy_male, 0) + COALESCE(weak_raised_male, 0)) > 0;

UPDATE t_farm_pig_farrow
SET female_count = COALESCE(healthy_female, 0) + COALESCE(weak_raised_female, 0)
WHERE (female_count IS NULL OR female_count = 0)
  AND (COALESCE(healthy_female, 0) + COALESCE(weak_raised_female, 0)) > 0;
