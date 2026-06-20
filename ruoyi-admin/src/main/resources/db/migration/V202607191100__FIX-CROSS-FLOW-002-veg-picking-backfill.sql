-- ============================================================
-- FIX-CROSS-FLOW-002 毛菜处理可见性回填
--
-- 背景：CROSS-FLOW-002 原仅在地块「采摘完成」时创建 t_warehouse_planting_record 待办，
--       导致已「开始采摘」(harvest_status='picking') 的地块在仓库毛菜处理不可见。
--       AppletPickServiceImpl 已改为「首次进入活跃采摘态(picking/completed)即发事件」，
--       本迁移回填存量：为已 picking/completed 但仓库无对应待办的 is_pick=2 地块补 1 行。
--
-- 幂等：NOT EXISTS (plot_id, crop_id) 跳过已有待办（采摘完成的旧数据不会重复），
--       Flyway 仅执行一次，本 SELECT 不影响游客采摘活动(is_pick=1)。
-- ============================================================

SET NAMES utf8mb4;

INSERT INTO t_warehouse_planting_record
    (tenant_id, plot_id, crop_id, plot_name, crop_name,
     plant_date, harvest_date, harvest_weight, is_loss,
     team_id, data_date, handle_status,
     create_by, create_time, remark, del_flag, del_unique)
SELECT
    '1001', d.plot_id, d.crop_id, pl.plot_name, c.crop_name,
    d.begin_actualdate,
    COALESCE(d.end_harvestdate, d.begin_harvestdate, CURDATE()),
    COALESCE(d.actual_yield, 0), 2,
    d.harvest_by, NOW(), 'pending',
    1, NOW(), 'CROSS-FLOW-002 picking 可见性回填', '0', 0
FROM t_plant_plant_details d
LEFT JOIN t_plant_plot_info pl ON pl.id = d.plot_id
LEFT JOIN t_plant_crop_info c  ON c.id = d.crop_id
WHERE d.del_flag = '0'
  AND d.tenant_id = '1001'
  AND d.is_pick = 2
  AND d.harvest_status IN ('picking', 'completed')
  AND NOT EXISTS (
      SELECT 1 FROM t_warehouse_planting_record r
       WHERE r.plot_id = d.plot_id AND r.crop_id = d.crop_id AND r.del_flag = '0'
  );
