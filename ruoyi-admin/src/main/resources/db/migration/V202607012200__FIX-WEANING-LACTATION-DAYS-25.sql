-- ============================================================
-- 哺乳天数（断奶期计算）由 28 → 25：断奶期 = 分娩日期 + lactation_days。
-- mp 选猪卡「到断奶期」提示按此周期算（PigCoreServiceImpl.computeDueDateMap WEANING 分支）。
-- getValue 优先 custom_value，无则 default_value：seed custom_value=NULL → 改 default_value 即生效；
-- 兜底把残留 custom_value=28 一并归零，避免旧值盖过新默认。
-- ============================================================
UPDATE t_farm_production_cycle_config
SET default_value = 25
WHERE tenant_id = '1001'
  AND config_key = 'lactation_days';

UPDATE t_farm_production_cycle_config
SET custom_value = NULL
WHERE tenant_id = '1001'
  AND config_key = 'lactation_days'
  AND custom_value = 28;
