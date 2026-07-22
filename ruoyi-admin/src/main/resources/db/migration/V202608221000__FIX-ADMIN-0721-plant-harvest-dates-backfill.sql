-- FIX-ADMIN-0721（飞书 admin row3/row4）：种植采摘日期链存量回填。
-- ① 已完成明细 begin_harvestdate 缺失以 end_harvestdate 近似；② 主表 earliest/last_harvestdate 按明细聚合重算（口径对齐 PlantPlanMapper.recalcAggregates）。

UPDATE t_plant_plant_details
   SET begin_harvestdate = end_harvestdate
 WHERE harvest_status = 'completed'
   AND begin_harvestdate IS NULL
   AND end_harvestdate IS NOT NULL
   AND del_flag = '0';

UPDATE t_plant_plant_plan p
  JOIN (
       SELECT plant_id,
              MIN(earliest_harvestdate) AS min_h,
              MAX(last_harvestdate) AS max_h
         FROM t_plant_plant_details
        WHERE del_flag = '0'
        GROUP BY plant_id
       ) d ON d.plant_id = p.id
   SET p.earliest_harvestdate = d.min_h,
       p.last_harvestdate = d.max_h
 WHERE p.del_flag = '0';
