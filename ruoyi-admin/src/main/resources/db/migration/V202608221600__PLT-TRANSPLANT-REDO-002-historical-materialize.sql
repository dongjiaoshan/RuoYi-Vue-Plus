-- PLT-TRANSPLANT-REDO-002：历史已 100% 移栽 backfill——补目标满种明细 + 目标地块转种植态。
-- 旧副作用（REDO-001 前）在移栽累计 100% 时只把源育苗地块置空、从不把目标地块入计划。
-- 本迁移对历史「累计 ≥100%」的 (作物, 源育苗地块) 组，为每个目标地块补一条满种明细
-- （日期沿用源育苗明细、面积按目标地块、transplant_adjusted=1），并置目标地块 plot_status=2。
-- 源育苗地块旧已置空(plot_status=1)，已越过退茬，不回改。
-- id 复用移栽记录 snowflake（MP 全局唯一 ID，跨表不复用，作明细 id 不撞）。
-- 幂等：目标已有本计划明细则跳过（NOT EXISTS）——重复应用 / 与新代码落地共存均安全。

INSERT INTO t_plant_plant_details
  (id, tenant_id, plant_id, plot_id, crop_id, plant_month, plant_period,
   begin_actualdate, earliest_harvestdate, last_harvestdate,
   plant_status, harvest_status, plot_area, expected_yield, is_pick, plant_by,
   transplant_adjusted, create_dept, create_by, create_time, update_by, update_time, del_flag, del_unique)
SELECT
   MIN(fr.id),
   '1001', src.plant_id, fr.transplant_plot, fr.crop_id,
   src.plant_month, src.plant_period,
   src.begin_actualdate, src.earliest_harvestdate, src.last_harvestdate,
   'completed', 'pending', tp.plot_area,
   CASE WHEN tp.plot_area IS NOT NULL AND cr.predicted_per IS NOT NULL
        THEN tp.plot_area * cr.predicted_per ELSE 0 END,
   2, src.plant_by,
   1, src.create_dept, src.create_by, NOW(), src.create_by, NOW(), '0', 0
FROM t_plant_farm_records fr
JOIN (SELECT crop_id, plot_id
        FROM t_plant_farm_records
       WHERE farm_type = 'transplant' AND del_flag = '0' AND tenant_id = '1001'
       GROUP BY crop_id, plot_id
      HAVING COALESCE(SUM(transplant_percent), 0) >= 100) done
  ON done.crop_id = fr.crop_id AND done.plot_id = fr.plot_id
JOIN (SELECT plant_id, plot_id, crop_id, MIN(id) AS min_id
        FROM t_plant_plant_details
       WHERE del_flag = '0' AND tenant_id = '1001'
       GROUP BY plant_id, plot_id, crop_id) srcpick
  ON srcpick.plant_id = fr.plant_id AND srcpick.plot_id = fr.plot_id AND srcpick.crop_id = fr.crop_id
JOIN t_plant_plant_details src ON src.id = srcpick.min_id
JOIN t_plant_plot_info tp ON tp.id = fr.transplant_plot AND tp.del_flag = '0' AND tp.tenant_id = '1001'
JOIN t_plant_crop_info cr ON cr.id = fr.crop_id
WHERE fr.farm_type = 'transplant' AND fr.del_flag = '0' AND fr.tenant_id = '1001'
  AND fr.transplant_plot IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM t_plant_plant_details x
                   WHERE x.plant_id = src.plant_id AND x.plot_id = fr.transplant_plot AND x.del_flag = '0')
GROUP BY src.plant_id, fr.transplant_plot, fr.crop_id, src.plant_month, src.plant_period,
         src.begin_actualdate, src.earliest_harvestdate, src.last_harvestdate,
         tp.plot_area, cr.predicted_per, src.plant_by, src.create_dept, src.create_by;

-- 目标地块转种植态(2)——仅本次 backfill 生成的满种明细(transplant_adjusted=1)对应地块。
-- 迁移时点仅历史 backfill 行带该标记，不会误动其它地块。
UPDATE t_plant_plot_info tp
   SET tp.plot_status = 2, tp.update_time = NOW()
 WHERE tp.del_flag = '0' AND tp.tenant_id = '1001'
   AND EXISTS (SELECT 1 FROM t_plant_plant_details d
                WHERE d.plot_id = tp.id AND d.transplant_adjusted = 1 AND d.del_flag = '0');
