-- ============================================================
-- DJS-FIX-BREED-AUDIT-COLS-001：修 t_farm_status_record 运行库漂移缺列
--
-- 真因：V202605242200 无守卫 ALTER 的 version(202605242200) < flyway baseline(202605281100)，
--   Flyway 永不应用 baseline 及之前的 migration → 该 ALTER 从未被 Flyway 记录/执行
--   （flyway_schema_history 无该行，非 failed 行）。源 CREATE(V202605200901) 同样 < baseline 被跳过。
-- 物理表缺 update_by/update_time 时，PigStatusRecord(TenantEntity) 的 selectVoList 强制 SELECT
--   这两列 → Unknown column 'update_by' → /history、queryDetail、status-record/list 全 500。
-- 本文件 version 远高于 baseline，用 INFORMATION_SCHEMA 守卫幂等补列（缺则加，已有则跳过），
--   任何环境（新建 / 重建 / 重放）安全。
-- 不改源 CREATE(V202605200901) / 原 ALTER(V202605242200)（Flyway hash 不可逆）。
-- ============================================================
SET @db := DATABASE();

SET @has_ub := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_farm_status_record' AND COLUMN_NAME='update_by');
SET @sql := IF(@has_ub=0,
  "ALTER TABLE t_farm_status_record ADD COLUMN update_by BIGINT NULL COMMENT '更新人（MP insertFill 占位，状态记录实际不 update）' AFTER create_time",
  "DO 0");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @has_ut := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_farm_status_record' AND COLUMN_NAME='update_time');
SET @sql := IF(@has_ut=0,
  "ALTER TABLE t_farm_status_record ADD COLUMN update_time DATETIME NULL COMMENT '更新时间（MP insertFill 占位）' AFTER update_by",
  "DO 0");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
