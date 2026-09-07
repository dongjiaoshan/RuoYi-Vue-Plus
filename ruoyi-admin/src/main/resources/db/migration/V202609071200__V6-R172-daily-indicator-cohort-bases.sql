-- 仓库日指标：屠宰率 / 白条出品率 / 白条均重的 cohort 基数列（V6 row172）。
--
-- 这三个指标的分子分母必须来自同一批猪，而这批猪的口径各不相同：
--   · 屠宰率  ：当日在燎毛间完成称重的那批（称重 cohort）里「有出栏重量」的子集；
--   · 白条出品率：当日处理完成的那批（finish_time cohort）里「有接收重量」的子集（分子分母对称，防 >100%）；
--   · 白条均重：当日处理完成的那批（整 cohort）。
-- 日表只落最终比率的话，月表无法从日表还原（Σ日分子/Σ日分母 拿不到），故把五个 cohort 基数
-- 一起落盘，月表按 Σ基数 重算比率。

SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_warehouse_indicator_record'
               AND COLUMN_NAME = 'slaughter_rate_arrive_weight');
SET @s1 := IF(@c1 > 0, 'SELECT 1',
  "ALTER TABLE t_warehouse_indicator_record
     ADD COLUMN slaughter_rate_arrive_weight DECIMAL(12,3) NOT NULL DEFAULT 0.000
     COMMENT '屠宰率分子：当日燎毛间完成称重且有出栏重量的猪只，接收重量之和' AFTER arrive_weight");
PREPARE st1 FROM @s1;
EXECUTE st1;
DEALLOCATE PREPARE st1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_warehouse_indicator_record'
               AND COLUMN_NAME = 'slaughter_rate_base_weight');
SET @s2 := IF(@c2 > 0, 'SELECT 1',
  "ALTER TABLE t_warehouse_indicator_record
     ADD COLUMN slaughter_rate_base_weight DECIMAL(12,3) NOT NULL DEFAULT 0.000
     COMMENT '屠宰率分母：同一批猪的出栏重量之和（自养 bar.marketing_weight / 外购生猪 outsource_pig.pig_weight）' AFTER slaughter_rate_arrive_weight");
PREPARE st2 FROM @s2;
EXECUTE st2;
DEALLOCATE PREPARE st2;

SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_warehouse_indicator_record'
               AND COLUMN_NAME = 'finished_count');
SET @s3 := IF(@c3 > 0, 'SELECT 1',
  "ALTER TABLE t_warehouse_indicator_record
     ADD COLUMN finished_count INT NOT NULL DEFAULT 0
     COMMENT '当日处理完成的猪只头数（bar.finish_time 落当天）= 白条均重的分母' AFTER bar_total_weight");
PREPARE st3 FROM @s3;
EXECUTE st3;
DEALLOCATE PREPARE st3;

SET @c4 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_warehouse_indicator_record'
               AND COLUMN_NAME = 'finished_arrive_weight');
SET @s4 := IF(@c4 > 0, 'SELECT 1',
  "ALTER TABLE t_warehouse_indicator_record
     ADD COLUMN finished_arrive_weight DECIMAL(12,3) NOT NULL DEFAULT 0.000
     COMMENT '当日处理完成的猪只，接收重量之和 = 白条出品率的分母' AFTER finished_count");
PREPARE st4 FROM @s4;
EXECUTE st4;
DEALLOCATE PREPARE st4;

SET @c7 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_warehouse_indicator_record'
               AND COLUMN_NAME = 'bar_yield_numer_weight');
SET @s7 := IF(@c7 > 0, 'SELECT 1',
  "ALTER TABLE t_warehouse_indicator_record
     ADD COLUMN bar_yield_numer_weight DECIMAL(12,3) NOT NULL DEFAULT 0.000
     COMMENT '白条出品率分子：处理完成 ∩ 有接收重量子集的 in_weight 之和（与 finished_arrive_weight 同子集，保证率≤100%）' AFTER finished_arrive_weight");
PREPARE st7 FROM @s7;
EXECUTE st7;
DEALLOCATE PREPARE st7;

-- 存量日表行按旧口径的等价量回填：旧口径下屠宰率 = arrive_weight/slaughter_weight、
-- 白条出品率 = bar_total_weight/slaughter_weight、白条均重 = bar_total_weight/slaughter_count。
-- 填成下面这组基数后，这些行在月表里贡献的比率与它们落盘当时完全一致——重算只从 2026-08-19 起跑，
-- 之前的行不重算也不会因为新列为 0 把当月比率算歪。
-- 幂等只认「这次是否刚加列」（@c1=0 = 首次执行时列此前不存在）：只有首次才回填。
-- 不能按「基数全为 0」判存量——一个当日只出栏、无称重无完成的合法新口径行天然五列全 0，
-- 第二遍会被误当存量行凭空造出处理完成 cohort（D3）。列已存在（重跑）就整段跳过。
SET @bf := IF(@c1 = 0,
  "UPDATE t_warehouse_indicator_record
      SET slaughter_rate_arrive_weight = COALESCE(arrive_weight, 0),
          slaughter_rate_base_weight   = COALESCE(slaughter_weight, 0),
          finished_count               = COALESCE(slaughter_count, 0),
          finished_arrive_weight       = COALESCE(slaughter_weight, 0),
          bar_yield_numer_weight       = COALESCE(bar_total_weight, 0)",
  'SELECT 1');
PREPARE stbf FROM @bf;
EXECUTE stbf;
DEALLOCATE PREPARE stbf;

-- 出栏 cohort（marketing_time）/ 处理完成 cohort（finish_time）是重算时的日分桶键，
-- 日重算逐日扫全表，补上索引。
SET @c5 := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_warehouse_bar_info'
               AND INDEX_NAME = 'idx_bar_finish_time');
SET @s5 := IF(@c5 > 0, 'SELECT 1',
  'CREATE INDEX idx_bar_finish_time ON t_warehouse_bar_info (tenant_id, finish_time)');
PREPARE st5 FROM @s5;
EXECUTE st5;
DEALLOCATE PREPARE st5;

SET @c6 := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_warehouse_bar_info'
               AND INDEX_NAME = 'idx_bar_marketing_time');
SET @s6 := IF(@c6 > 0, 'SELECT 1',
  'CREATE INDEX idx_bar_marketing_time ON t_warehouse_bar_info (tenant_id, marketing_time)');
PREPARE st6 FROM @s6;
EXECUTE st6;
DEALLOCATE PREPARE st6;
