-- FIX-DROP-ORPHAN-STORE-CHECK-RECORD-001：删除孤儿表 t_store_check_record（审计 P3-1）
--
-- t_store_check_record 已被 t_store_daily_ledger 取代，审计确认 0 代码引用。

DROP TABLE IF EXISTS t_store_check_record;
