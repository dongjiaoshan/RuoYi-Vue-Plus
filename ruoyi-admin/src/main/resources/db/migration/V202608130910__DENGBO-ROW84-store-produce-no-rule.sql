-- 门店打包生产编码规则种子（测试问题 row84）。
-- STORE_PRODUCE_NO  pattern {prefix}{yyMMdd}{seq4}，daily_reset=3（按门店生产标识码分桶）。
--   prefix = 门店 production_mark_code（row83 新增字段），由门店服务传入 context.prefix；
--   seq_date = yyMMdd + prefix 复合键，每个门店当日各自从 0001 起算。
--   最终形如 <生产标识码>YYMMDD####（例 DJS012603130001）。
-- tenant_id 不显式赋值（DEFAULT '1001'）。INSERT IGNORE 幂等。
INSERT IGNORE INTO t_md_biz_code_rule
  (code_type,          pattern,                  daily_reset, prefix, seq_length, status, create_by, create_time)
VALUES
  ('STORE_PRODUCE_NO', '{prefix}{yyMMdd}{seq4}',  3,           '',     4,          '0',    1,         NOW());
