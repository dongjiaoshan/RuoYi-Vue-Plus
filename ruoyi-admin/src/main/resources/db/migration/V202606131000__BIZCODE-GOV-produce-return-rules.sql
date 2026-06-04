-- ============================================================
-- BizCodeType 统一治理：产品生产编号 + 退货单号规则种子
--
-- PRODUCE_NO  每日重置 + 按业态前缀分桶（daily_reset=3）
--   pattern {yyMMdd}{prefix}{seq4}，prefix 由 service 按 product_info.belong_type 传入
--   （Z=猪肉 / G=果蔬 / B=白条 / H=干货 / D=鸡蛋 / L=礼盒）；
--   底层 1 条规则，序号按 seq_date = yyMMdd + prefix 复合键独立递增，
--   使每业态当日各自从 0001 起算。对齐 doc/11 §2.6 R7。
--   替代 WMS-PACK-001 inline selectMaxProduceNoByPrefix + 应用层自增。
--
-- RETURN_NO   每日重置（daily_reset=1）
--   pattern RET{yyyyMMdd}{seq4}，与 BURN_NO / CUT_NO / BAR_NO 范式一致。
--   替代 WMS-SHIP-001 inline selectMaxReturnSeqByDate + 应用层自增。
--
-- tenant_id 不显式赋值（DEFAULT '1001'）。INSERT IGNORE 幂等。
-- ============================================================

INSERT IGNORE INTO t_md_biz_code_rule
  (code_type,    pattern,                  daily_reset, prefix, seq_length, status, create_by, create_time)
VALUES
  ('PRODUCE_NO', '{yyMMdd}{prefix}{seq4}',  3,           '',     4,          '0',    1,         NOW()),
  ('RETURN_NO',  'RET{yyyyMMdd}{seq4}',     1,           'RET',  4,          '0',    1,         NOW());
