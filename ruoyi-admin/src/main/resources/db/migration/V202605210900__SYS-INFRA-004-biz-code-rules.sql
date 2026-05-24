-- ============================================================
-- SYS-INFRA-004 业务编码规则种子数据
--   9 类编码：
--     业务单据（每日重置 5 + 终生 1）：EAR_NO / DEMAND_NO / SHIP_NO / PACK_NO / STOCK_FLOW_NO / TRACE_CODE
--     主数据（终生递增 3）：MEMBER_NO / STORE_CODE / SUPPLIER_CODE
--   tenant_id 不显式赋值（DEFAULT '1001'，对齐 D01 SYS-INIT-001 规约）
-- ============================================================

INSERT IGNORE INTO t_md_biz_code_rule
  (code_type,       pattern,                                   daily_reset, prefix, seq_length, status, create_by, create_time)
VALUES
  ('EAR_NO',        '{farmCode2}{barnCode2}{yyMM}{dailySeq4}', 1,           '',     4,          '0',    1,         NOW()),
  ('TRACE_CODE',    'T{yyyyMMdd}{productCode2}{seq6}',         0,           'T',    6,          '0',    1,         NOW()),
  ('DEMAND_NO',     'D{yyyyMMdd}{bizCode2}{seq4}',             1,           'D',    4,          '0',    1,         NOW()),
  ('SHIP_NO',       'S{yyyyMMdd}{seq4}',                       1,           'S',    4,          '0',    1,         NOW()),
  ('PACK_NO',       'P{yyyyMMdd}{seq4}',                       1,           'P',    4,          '0',    1,         NOW()),
  ('STOCK_FLOW_NO', 'F{yyyyMMdd}{ioCode2}{seq4}',              1,           'F',    4,          '0',    1,         NOW()),
  ('MEMBER_NO',     'M{seq4}',                                 0,           'M',    4,          '0',    1,         NOW()),
  ('STORE_CODE',    'ST{seq4}',                                0,           'ST',   4,          '0',    1,         NOW()),
  ('SUPPLIER_CODE', 'G{seq4}',                                 0,           'G',    4,          '0',    1,         NOW());
