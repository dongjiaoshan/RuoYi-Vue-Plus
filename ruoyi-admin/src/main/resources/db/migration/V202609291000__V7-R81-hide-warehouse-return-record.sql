-- V7 row81（甲方2026-11-08）：只隐藏仓库退回记录入口，历史记录/角色权限保留。
-- 取号：2026-11-09已查staging max=202609281200；下一仓库10:00槽满足>1h。
UPDATE sys_menu
SET visible = '1', update_time = NOW()
WHERE menu_id = 9210
  AND component = 'djs-warehouse/return/index';
