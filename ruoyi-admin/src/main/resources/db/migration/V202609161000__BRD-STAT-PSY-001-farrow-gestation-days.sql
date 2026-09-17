-- ============================================================================
-- BRD-STAT-PSY-001  日表新增「日分娩猪只妊娠天数」+ 年表口径列注释校准
--
-- 甲方 2026-09-16 / Kevin 2026-09-17 定（V6 row227 / row228）：
--   口径出处：D-0086（PSY 收口）· D-0084（PSY 分子=算法2）· D-0085（除数 115）· D-0087（年分娩率）
--            · D-0088（妊娠天数上限）· D-0082（超期在怀照常计入）
--   PSY        =（当年日妊娠天数之和 ÷ 母猪头日）×（365 ÷ 115）× 窝均断奶数
--                「日妊娠天数」= 当天有多少头母猪怀着（甲方 2026-09-16 明确为「算法2」）→ 新列 pregnant_sow_count
--                115 = 正常妊娠期（祝碧「猪都是 115 天分娩」/ 王尉「我们自己都用的 115 天」/ 邓博「那就用 115」）
--   年分娩率   = 年分娩头数 ÷ Σ月表 mate_litter_count × 100
-- 分子这一项全项目原本不存在，本迁移补上并回填历史。
--
-- 落在日表而不是年表直算，是为了让分子与分母（Σ日期末生产母猪头数）锚同一个日表区间——
-- 日表 2026-07-17 才起，分子若直扫底表会把 7 月中以前的分娩算进去而分母没有对应天数。
--
-- 幂等：加列前查 information_schema；回填是「从底表全量重算」，重跑结果相同。
-- V202609161000 > 当前 flyway max V202609151030。
-- ============================================================================
SET NAMES utf8mb4;

-- 判定节点天数：与 DashboardServiceImpl#farrowJudgeDeadlineDays() 逐字同源
-- （getValue = COALESCE(custom_value, default_value)，非正回退 119），
-- 不写字面量 —— 写死会让回填出来的老行与按配置算的新行两套口径并存。
SET @judge := (SELECT COALESCE(custom_value, default_value)
                 FROM t_farm_production_cycle_config
                WHERE config_key = 'sow_farrow_judge_deadline_days' AND del_flag = '0'
                LIMIT 1);
SET @judge := IF(@judge IS NULL OR @judge <= 0, 119, @judge);

-- ------------------------------------------------------------
-- 1. 日表加列
-- ------------------------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_farm_indicator_record'
               AND COLUMN_NAME = 'farrow_gestation_days');
SET @s1 := IF(@c1 > 0, 'SELECT 1',
  "ALTER TABLE t_farm_indicator_record
     ADD COLUMN farrow_gestation_days INT NULL DEFAULT 0
     COMMENT '日分娩猪只妊娠天数（Σ当日分娩母猪 分娩日−配种日，按母猪去重、超出 0~判定节点天数 的整行剔除）'");
PREPARE st1 FROM @s1;
EXECUTE st1;
DEALLOCATE PREPARE st1;

-- ------------------------------------------------------------
-- 1b. 日表加「当日妊娠母猪头数」（PSY 分子）
--
--     甲方 2026-09-16 澄清：PSY 式里的「日妊娠天数」是「当天有多少头怀着」，逐日计数，
--     不是上面那列（那列在分娩当天把整个孕期一次性计入，是另一个量，甲方 row227 单独要的）。
--     PZ = 配种后未分娩那一段（状态机 BREED → PZ、FARROW: PZ → FM），FM 已分娩不计。
--     **不按判定节点截断**（D-0082 Kevin 2026-09-17）：配种超期仍挂 PZ 的照常算在怀 —— 这一列如实反映
--     系统里记着的状态。代价是结论没录进来时 PSY 会持续虚高（staging 实测 83 头在怀里 29 头超期、
--     分子虚高 12% 且逐日增长），改由 upsertAnnualIndicator 的告警提示去补录，不靠指标纠偏。
--     与 fillEndStock 的 PZ 分桶同口径。
-- ------------------------------------------------------------
SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_farm_indicator_record'
               AND COLUMN_NAME = 'pregnant_sow_count');
SET @s2 := IF(@c2 > 0, 'SELECT 1',
  "ALTER TABLE t_farm_indicator_record
     ADD COLUMN pregnant_sow_count INT NULL DEFAULT 0
     COMMENT '当日在怀母猪头数（快照 current_status=PZ 的种母猪，不按判定节点截断）；Σ日 = 妊娠头日 = PSY 分子'");
PREPARE st2 FROM @s2;
EXECUTE st2;
DEALLOCATE PREPARE st2;

-- ------------------------------------------------------------
-- 2. 回填历史日表
--
--    定时任务 DashboardAggregateJob 是 60 天滚动窗（rebuildDays=60），够不着日表最早那几天：
--    staging 日表起点 2026-07-17，首次夜跑窗口是 7/19~9/16，而 7/18 有一窝真实分娩。
--    不回填 → 那些日子恒为 0 → 年表 PSY 分子静默偏小（staging 实测 684 → 570，低估 16.7%），
--    且不报任何错。同目录 V202609071200 / V202609071500 / V202609081000 都带回填段，本迁移照做。
--
--    口径必须与 AggregateQueryMapper#sumFarrowGestationDaysForDay 逐字一致：
--    按 pig_id 去重取 MAX（对齐同行的 farrow_sow_count = COUNT(DISTINCT pig_id)）。取 MAX 不是 MIN 是
--    两害取其轻：挂错配种两个方向都有（当天先录新配种 → dd 偏小、能到 0；本轮配种没录 → 回落到上一轮
--    → dd 偏大），MIN 会被 dd=0 把真窝整条抹成 0，MAX 只会被一条 in-range 的偏大脏行盖住真值。
--    妊娠天数落在 0~judgeDays 之外的整行剔除（放 WHERE，不套 GREATEST 夹 0 —— 夹 0 只是把脏行
--    变成一个 0 值候选留在组里，滤掉才是「这行算不出妊娠天数」的正确表达）。
--    上界 = judgeDays（配置 sow_farrow_judge_deadline_days，缺省 119），出自祝碧 2026-09-16 的财务规则
--    「超过 119 天还不分娩就不能录分娩了」。超出的多半是挂错了
--    配种（分娩没录进系统时会挂到该母猪上一轮配种上，能算出几百天），不是真实妊娠期。
-- ------------------------------------------------------------
UPDATE t_farm_indicator_record r
   SET r.farrow_gestation_days = COALESCE((
        SELECT SUM(g.dd) FROM (
          SELECT MAX(DATEDIFF(DATE(f.farrow_date), DATE(b.breeding_date))) AS dd
            FROM t_farm_pig_farrow f
            JOIN t_farm_pig_breeding b ON b.id = f.breeding_id AND b.del_flag = '0'
           WHERE f.tenant_id = r.tenant_id AND f.del_flag = '0'
             AND f.farrow_date >= r.stat_date
             AND f.farrow_date <  r.stat_date + INTERVAL 1 DAY
             AND DATEDIFF(DATE(f.farrow_date), DATE(b.breeding_date)) BETWEEN 0 AND @judge
           GROUP BY f.pig_id) g), 0)
 WHERE r.del_flag = '0';

UPDATE t_farm_indicator_record r
   SET r.pregnant_sow_count = COALESCE((
        SELECT COUNT(*) FROM t_farm_pig_snapshot s
         WHERE s.tenant_id = r.tenant_id AND s.snap_date = r.stat_date
           AND s.pig_type = 'sow' AND s.current_status = 'PZ'), 0)
 WHERE r.del_flag = '0';

-- ------------------------------------------------------------
-- 3. 年表列注释校准（甲方是照着表名/列名提的单，注释就是他们读到的口径说明）
--
--    这批注释停在改造前的旧口径上，其中 total_npd_days 那条写的正是甲方本次投诉的算法
--    （「Σ日230后备+Σ日非生产母猪」）—— 实际代码早已不含 230 后备，只有注释还在误导。
--    列定义逐字保持原样，只换 COMMENT。
-- ------------------------------------------------------------
ALTER TABLE t_farm_year_production
  MODIFY COLUMN total_npd_days INT NULL DEFAULT 0
    COMMENT '全年总NPD天数（Σ日非生产母猪头数，不含230后备）',
  MODIFY COLUMN year_batch_farrow_count INT NULL DEFAULT 0
    COMMENT '年分娩头数（Σ月表 cohort_farrow_count，判定节点内分娩的头数；= 年分娩率分子）',
  MODIFY COLUMN year_farrow_rate DECIMAL(12,3) NULL DEFAULT 0.000
    COMMENT '年分娩率%（年分娩头数 / Σ月表 mate_litter_count × 100）',
  MODIFY COLUMN cohort_matured_count INT NOT NULL DEFAULT 0
    COMMENT '判定节点落在本年且已到期的配种批次数（仅作对账参考，不是分娩率分母）',
  MODIFY COLUMN psy_stat_from DATE NULL
    COMMENT 'PSY/非生产天数的统计区间起始日（= 当年日表实际覆盖的第一天）',
  MODIFY COLUMN psy_stat_days INT NOT NULL DEFAULT 0
    COMMENT 'PSY/非生产天数的统计区间天数（= 该区间已落盘日表行数；平均非生产天数的年化乘数 365/该值，PSY 不用它）',
  MODIFY COLUMN psy DECIMAL(8,2) NULL
    COMMENT 'PSY 每头母猪年产断奶仔猪数（（Σ日妊娠天数/母猪头日）×365/115×窝均断奶数）';

-- ------------------------------------------------------------
-- 4. 月表列注释校准
--
--    甲方 row228 原文直接点名了 mat_litter_count 这一列，它的注释却还停在建表时的旧口径
--    「每日 当天−114 配种母猪数累加」—— as-built 早已改成 cohort 口径（判定日落在本月的配种批次数），
--    年表的年分娩率正是 Σ 这一列。注释不改，甲方照着列名读到的仍是错的。
--    列定义逐字保持原样，只换 COMMENT。
-- ------------------------------------------------------------
ALTER TABLE t_farm_monthly_production
  MODIFY COLUMN mate_litter_count INT NULL DEFAULT 0
    COMMENT '当月累计匹配配种窝数 = 判定节点（配种日+judgeDays）落在本月的配种批次数；月/年分娩率分母',
  MODIFY COLUMN farrow_rate DECIMAL(12,3) NULL DEFAULT 0.000
    COMMENT '分娩率%（cohort_farrow_count / mate_litter_count × 100，同一批 cohort）';
