-- R160-R161：回填历史分娩记录的产仔总重 total_weight + 平均出生重 avg_weight。
-- 背景：耳标提交同步 farrow 权重（BRD-EVENT-003 syncFarrowWeights）上线前已打标的窝，
-- t_farm_pig_farrow.total_weight / avg_weight 为 NULL → mp 分娩记录「仔猪均重」显 —、
-- 母猪性能「平均出生重」聚合 Σavg_weight 得 0。按已标仔猪 t_farm_pig_pigletno.birth_weight 累计回填。
-- 口径与 syncFarrowWeights 一致：total = Σ出生重；avg = Σ / 有出生重头数。幂等（仅填 NULL 行）。
UPDATE t_farm_pig_farrow f
JOIN (
    SELECT farrow_id,
           ROUND(SUM(birth_weight), 2)                       AS tw,
           ROUND(SUM(birth_weight) / COUNT(birth_weight), 2) AS aw
    FROM t_farm_pig_pigletno
    WHERE del_flag = '0' AND birth_weight IS NOT NULL
    GROUP BY farrow_id
) agg ON CAST(agg.farrow_id AS SIGNED) = CAST(f.id AS SIGNED)
SET f.total_weight = agg.tw,
    f.avg_weight   = agg.aw
WHERE f.del_flag = '0'
  AND (f.total_weight IS NULL OR f.avg_weight IS NULL);
