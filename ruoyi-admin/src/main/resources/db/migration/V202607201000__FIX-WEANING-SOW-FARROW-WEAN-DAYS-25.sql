-- ============================================================
-- 断奶期 = 最近分娩日 + sow_farrow_to_wean_days（PigCoreServiceImpl.computeDueDateMap WEANING 分支读此 key）。
-- getValue 优先 custom_value，无则 default_value。staging 该 key 残留 custom_value=35（admin 母猪生产配置表单写过），
-- 盖过默认 25，导致断奶期算成 分娩日+35。本迁移把 custom_value 归 NULL 回落到 default_value，并防御性确保 default=25。
-- 注：前一迁移 V202607012200 改的是死 key lactation_days（代码不读），未生效，本迁移修正真正生效的 key。
-- ============================================================
UPDATE t_farm_production_cycle_config
SET custom_value = NULL
WHERE tenant_id = '1001'
  AND config_key = 'sow_farrow_to_wean_days'
  AND del_flag = '0';

UPDATE t_farm_production_cycle_config
SET default_value = 25
WHERE tenant_id = '1001'
  AND config_key = 'sow_farrow_to_wean_days'
  AND del_flag = '0';
