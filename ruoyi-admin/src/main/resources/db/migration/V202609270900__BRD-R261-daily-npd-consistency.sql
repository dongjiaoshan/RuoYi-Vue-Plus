-- V6 row261 / D-0129: the daily derived value includes pregnancy-loss days.
-- Reconcile existing daily records without changing source counts/events or monthly/yearly values.
-- A repeated application makes no further changes.
UPDATE t_farm_indicator_record
SET npd_days = COALESCE(end_nonprod_sow_count, 0) + COALESCE(preg_loss_days, 0)
WHERE NOT (npd_days <=> COALESCE(end_nonprod_sow_count, 0) + COALESCE(preg_loss_days, 0));
