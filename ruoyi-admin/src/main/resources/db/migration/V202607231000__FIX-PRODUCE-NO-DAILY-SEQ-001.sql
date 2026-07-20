-- ============================================================
-- 生产编码规则改为「年月日 + 4 位每日自增」（去业态前缀分桶）
--
-- 旧：PRODUCE_NO  {yyMMdd}{prefix}{seq4}，daily_reset=3（按业态前缀 Z/G/B/H/D/L 各自当日独立递增）
-- 新：PRODUCE_NO  {yyMMdd}{seq4}，daily_reset=1（全打包共用一个每日计数器，当日从 0001 起算）
--     例：260512 + 0001 = 2605120001。
--
-- 追溯标签「生产编码」即取 product_production.produce_no，本规则一改即全部打包/出库生码统一格式。
-- tenant_id 不显式赋值（DEFAULT '1001'）。
-- ============================================================

UPDATE t_md_biz_code_rule
   SET pattern     = '{yyMMdd}{seq4}',
       daily_reset = 1,
       prefix      = '',
       seq_length  = 4
 WHERE code_type   = 'PRODUCE_NO'
   AND tenant_id   = '1001';
