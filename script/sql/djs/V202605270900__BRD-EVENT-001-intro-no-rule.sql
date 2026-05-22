-- ============================================================
-- BRD-EVENT-001 引种单号编码规则（INTRO_NO）
--   pattern: INT{yyyyMMdd}{seq4}     例 INT202605260001（每日重置 4 位序号）
-- ============================================================

INSERT IGNORE INTO t_md_biz_code_rule
  (code_type, pattern,                  daily_reset, prefix, seq_length, status, create_by, create_time)
VALUES
  ('INTRO_NO', 'INT{yyyyMMdd}{seq4}',   1,           'INT',  4,          '0',    1,         NOW());
