-- 白条出品率的分母基数列 + 猪肉段列注释对齐代码（V6 row172）。
--
-- 出品率 = Σ白条重 ÷ Σ出栏重量 ×100，两边都只取「当日处理完成 ∩ 出栏重量非空」的同一批猪
-- （客户口径「完成接收重量的猪只出栏重量之和」——「完成接收重量的猪只」是限定语、不是第二个日期锚；
--  分子按处理完成日、分母按称重日各取一批的话两批猪不同，出品率会破 100%）。
-- 日表落 bar_yield_base_weight 这个分母基数，月表按 Σ分子 ÷ Σ分母 重算。
-- finished_arrive_weight 保留为诊断列（这批猪的到场重之和），不再参与出品率。

SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_warehouse_indicator_record'
               AND COLUMN_NAME = 'bar_yield_base_weight');
SET @s1 := IF(@c1 > 0, 'SELECT 1',
  "ALTER TABLE t_warehouse_indicator_record
     ADD COLUMN bar_yield_base_weight DECIMAL(12,3) NOT NULL DEFAULT 0.000
     COMMENT '白条出品率分母：处理完成 ∩ 出栏重量非空子集的出栏重量之和（自养 bar.marketing_weight / 外购生猪 outsource_pig.pig_weight）' AFTER bar_yield_numer_weight");
PREPARE st1 FROM @s1;
EXECUTE st1;
DEALLOCATE PREPARE st1;

-- 存量行按旧口径的等价量回填：旧口径下出品率分母是送宰总重，填成它后这些行在月表里贡献的比率
-- 与它们落盘当时一致，不会因新列全 0 把当月比率算歪（重算只从 2026-08-19 起跑）。
-- 幂等守卫按 bar_yield_base_weight 这一列自己判：@c1 = 0 = 本次才加的列 = 首次执行，才回填；
-- 列已存在（重跑）整段跳过。不能按「基数为 0」判存量——一个当日无处理完成猪的合法新口径行天然为 0，
-- 第二遍会被误当存量行凭空造出分母。
SET @bf := IF(@c1 = 0,
  "UPDATE t_warehouse_indicator_record
      SET bar_yield_base_weight = COALESCE(slaughter_weight, 0)",
  'SELECT 1');
PREPARE stbf FROM @bf;
EXECUTE stbf;
DEALLOCATE PREPARE stbf;

-- 猪肉段列注释对齐当前代码口径（MODIFY 只改注释、天然幂等）。
ALTER TABLE t_warehouse_indicator_record
  MODIFY COLUMN slaughter_count INT DEFAULT 0
    COMMENT '屠宰头数：当日送宰的猪只头数 = 自养 bar.marketing_time + 外购生猪 outsource_pig.slaughter_date。统计送宰不是出栏，出栏头数在养殖模块统计',
  MODIFY COLUMN slaughter_weight DECIMAL(12,3) DEFAULT 0.000
    COMMENT '送宰总重：当日送宰的自养猪总重 Σ bar.marketing_weight + 外购生猪总重 Σ outsource_pig.pig_weight',
  MODIFY COLUMN arrive_weight DECIMAL(12,3) DEFAULT 0.000
    COMMENT '接收重量：当日在燎毛间完成称重的猪只总重 = Σ bar.arrive_weight（按 arrive_time 分桶）',
  MODIFY COLUMN slaughter_rate DECIMAL(12,3) DEFAULT NULL
    COMMENT '屠宰率%（屠宰率分子/屠宰率分母×100，同一批猪；分母 0 → NULL）',
  MODIFY COLUMN bar_total_weight DECIMAL(12,3) DEFAULT 0.000
    COMMENT '白条总重：当日处理完成（bar.finish_time）的全部猪只 Σ bar.in_weight，一行 = 一头猪 = 一个耳号',
  MODIFY COLUMN finished_arrive_weight DECIMAL(12,3) NOT NULL DEFAULT 0.000
    COMMENT '当日处理完成的猪只，接收重量之和（诊断列，不参与出品率）',
  MODIFY COLUMN bar_yield_numer_weight DECIMAL(12,3) NOT NULL DEFAULT 0.000
    COMMENT '白条出品率分子：处理完成 ∩ 出栏重量非空子集的 in_weight 之和（与 bar_yield_base_weight 同子集，保证率≤100%）',
  MODIFY COLUMN avg_bar_weight DECIMAL(12,3) DEFAULT NULL
    COMMENT '白条均重（白条总重/处理完成头数；分母 0 → NULL）',
  MODIFY COLUMN bar_yield_rate DECIMAL(12,3) DEFAULT NULL
    COMMENT '白条出品率%（出品率分子/出品率分母×100；分母 0 → NULL）';
