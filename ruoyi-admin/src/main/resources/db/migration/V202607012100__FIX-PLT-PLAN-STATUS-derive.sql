-- FIX-PLT-PLAN-STATUS-derive：一次性回填种植计划主表 plant_status（派生口径，仅 UPDATE 非破坏）。
--
-- 口径（Kevin 拍定，按"完成种植"计数判定）：
--   明细全部完成（done = total）→ 'completed'（已完成）
--   任一明细完成（done > 0）    → 'ongoing'（进行中/已开始）
--   0 完成（含无明细）          → 'pending'（待开始）
-- done = 子表 plant_status='completed' 计数；total = 子表有效明细行数。
-- delayed（延期）不在此派生范围，保持现状不改。
--
-- 运行后行为由 PlantPlanServiceImpl.finishPlant / startPlant 末尾 recalcPlanStatus 持续维护。
-- tenant_id 默认 '1001'（V1 单租户），不显式过滤。
UPDATE t_plant_plant_plan p
LEFT JOIN (
    SELECT plant_id,
           COUNT(1) AS total,
           SUM(plant_status = 'completed') AS done
    FROM t_plant_plant_details
    WHERE del_flag = '0'
    GROUP BY plant_id
) d ON p.id = d.plant_id
SET p.plant_status = CASE
        WHEN d.total IS NULL OR d.total = 0 THEN 'pending'
        WHEN d.done = d.total THEN 'completed'
        WHEN d.done > 0 THEN 'ongoing'
        ELSE 'pending' END
WHERE p.del_flag = '0';
