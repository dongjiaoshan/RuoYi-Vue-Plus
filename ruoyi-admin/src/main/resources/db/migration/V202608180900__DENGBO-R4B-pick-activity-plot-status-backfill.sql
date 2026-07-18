-- DENGBO row4（何涛 2026-07-17）：采摘活动地块采摘中/采摘完成后 plot_status 未流转的历史回填。
-- 背景：adjustPlotPickStatus 原只改 plant_details.harvest_status，不动 plot_info.plot_status，
--   导致采摘活动（is_pick=1）地块采摘后 plot_status 仍停在 2（种植），既不反映真实采摘态，
--   又无法退茬（submitRotation 要求 plot_status=3）、无法回落空地供下一轮播种。
-- 代码已修（adjustPlotPickStatus 进入采摘即 plot_status 2→3）；此处回填存量：
--   仍处采摘中/采摘完成、但 plot_status 停在 2 的采摘活动地块 → 3（采摘）。
-- 字典 djs_plot_status：1=空闲 / 2=种植 / 3=采摘。幂等：只改 plot_status=2 的行。
UPDATE t_plant_plot_info p
SET p.plot_status = 3,
    p.update_time = NOW()
WHERE p.tenant_id = '1001'
  AND p.del_flag = '0'
  AND p.plot_status = 2
  AND EXISTS (
      SELECT 1
      FROM t_plant_plant_details d
      WHERE d.plot_id = p.id
        AND d.tenant_id = '1001'
        AND d.del_flag = '0'
        AND d.is_pick = 1
        AND d.harvest_status IN ('picking', 'completed')
  );
