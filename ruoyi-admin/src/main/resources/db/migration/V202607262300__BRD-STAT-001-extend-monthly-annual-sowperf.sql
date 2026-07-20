-- BRD-STAT-001 — 扩列月表/年表/母猪性能表，补 row11/13/14 高级指标列。
--
--   t_farm_monthly_production  +row13 高级指标（配种窝数 / 分娩率 / 配种率 / 断配间隔 / NPD / 窝均 / 损失率）
--   t_farm_annual_indicator    +row14 高级指标（年均能繁存栏 / 断配间隔总量 / NPD / 年分娩率 / PSY 输入）
--   t_farm_sow_performance     +row11 指标算法列（平均怀孕天数 / 断配天数 / 返空流 / 窝均 / NPD）
--
-- 全部 ALTER ADD COLUMN（项目主流，非 IF NOT EXISTS；MySQL 不支持列级 IF NOT EXISTS，
--   本表为新增列、首次执行无冲突，依赖 Flyway 版本顺序保证只跑一次）。
-- 头数类 INT，重量/天数/率类 DECIMAL(12,3)。
-- V202607262300 > V202607262200。

SET NAMES utf8mb4;

-- ============================================================
-- 1. t_farm_monthly_production（row13）
-- ============================================================
ALTER TABLE t_farm_monthly_production
  ADD COLUMN mate_litter_count        INT           NULL DEFAULT 0 COMMENT '当月累计匹配配种窝数（每日 当天−114 配种母猪数累加）',
  ADD COLUMN farrow_rate              DECIMAL(12,3) NULL DEFAULT 0 COMMENT '分娩率%（当月分娩头数/累计匹配配种窝数×100）',
  ADD COLUMN breed_rate               DECIMAL(12,3) NULL DEFAULT 0 COMMENT '配种率%（当月配种母猪数/((Σ日生产母猪+Σ日230后备)/当月天数)×100）',
  ADD COLUMN wean_breed_interval      DECIMAL(12,3) NULL DEFAULT 0 COMMENT '断配间隔（当月断到配天数之和/完成断到配母猪头数）',
  ADD COLUMN abnormal_count           INT           NULL DEFAULT 0 COMMENT '返空流头数（当月返空流母猪头数）',
  ADD COLUMN npd_days                 DECIMAL(12,3) NULL DEFAULT 0 COMMENT '月均NPD天数',
  ADD COLUMN total_born_count         INT           NULL DEFAULT 0 COMMENT '总产仔数（当月 SUM total_born）',
  ADD COLUMN avg_born_per_litter      DECIMAL(12,3) NULL DEFAULT 0 COMMENT '窝均总产仔数（总产仔/分娩母猪头数）',
  ADD COLUMN avg_live_born_per_litter DECIMAL(12,3) NULL DEFAULT 0 COMMENT '窝均活仔（总活仔/分娩母猪头数）',
  ADD COLUMN avg_weaned_per_litter    DECIMAL(12,3) NULL DEFAULT 0 COMMENT '窝均断奶（断奶仔猪数/断奶母头数）',
  ADD COLUMN farrow_loss_rate         DECIMAL(12,3) NULL DEFAULT 0 COMMENT '分娩舍损失率（当月死亡仔猪数/当月总活仔数）';

-- ============================================================
-- 2. t_farm_annual_indicator（row14）
-- ============================================================
ALTER TABLE t_farm_annual_indicator
  ADD COLUMN avg_breeding_sow_stock   DECIMAL(12,3) NULL DEFAULT 0 COMMENT '年均能繁母猪存栏数（(Σ日生产母猪+Σ日230后备)/已历天数）',
  ADD COLUMN breeding_count           INT           NULL DEFAULT 0 COMMENT '年配种头数（当年总配种次数）',
  ADD COLUMN breed_rate               DECIMAL(12,3) NULL DEFAULT 0 COMMENT '年配种率（Σ日配种次数/年均能繁母猪存栏）',
  ADD COLUMN total_born_count         INT           NULL DEFAULT 0 COMMENT '总产仔数（当年 Σ日总产仔）',
  ADD COLUMN farrow_count             INT           NULL DEFAULT 0 COMMENT '年分娩次数（当年 Σ日分娩头数）',
  ADD COLUMN total_live_born          INT           NULL DEFAULT 0 COMMENT '总活仔数（当年 Σ日总活仔）',
  ADD COLUMN avg_live_born_per_litter DECIMAL(12,3) NULL DEFAULT 0 COMMENT '窝均活仔数（总活仔/年分娩次数）',
  ADD COLUMN total_weaned_piglet      INT           NULL DEFAULT 0 COMMENT '总断奶仔猪数（当年 Σ日断奶仔猪头数）',
  ADD COLUMN total_weaned_sow         INT           NULL DEFAULT 0 COMMENT '总断奶母猪头数（当年 Σ日断奶母猪头数）',
  ADD COLUMN avg_weaned_per_litter    DECIMAL(12,3) NULL DEFAULT 0 COMMENT '窝均断奶数（总断奶仔猪/总断奶母猪）',
  ADD COLUMN wean_breed_total_days    INT           NULL DEFAULT 0 COMMENT '当年断配间隔总天数',
  ADD COLUMN wean_breed_total_count   INT           NULL DEFAULT 0 COMMENT '当年断配间隔总记录数',
  ADD COLUMN wean_breed_interval      DECIMAL(12,3) NULL DEFAULT 0 COMMENT '断配间隔（总天数/总记录数）',
  ADD COLUMN total_npd_days           INT           NULL DEFAULT 0 COMMENT '全年总NPD天数（Σ日230后备+Σ日非生产母猪）',
  ADD COLUMN avg_npd_days             DECIMAL(12,3) NULL DEFAULT 0 COMMENT '年均NPD天数（总NPD/年均能繁存栏）',
  ADD COLUMN year_batch_farrow_count  INT           NULL DEFAULT 0 COMMENT '年分娩头数（Σ日当年配种批次分娩头数）',
  ADD COLUMN year_farrow_rate         DECIMAL(12,3) NULL DEFAULT 0 COMMENT '年分娩率%（年分娩头数/年配种头数×100）',
  ADD COLUMN avg_marketing_weight     DECIMAL(12,3) NULL DEFAULT 0 COMMENT '平均出栏重（Σ日出栏总重/Σ日出栏头数）',
  ADD COLUMN farrow_loss_rate         DECIMAL(12,3) NULL DEFAULT 0 COMMENT '分娩舍损失率（当年死亡仔猪数/总活仔数）';

-- ============================================================
-- 3. t_farm_sow_performance（row11）
-- ============================================================
ALTER TABLE t_farm_sow_performance
  ADD COLUMN avg_gestation_days       DECIMAL(12,3) NULL COMMENT '平均怀孕天数（配种到分娩天数之和/状态变化次数）',
  ADD COLUMN wean_breed_days          DECIMAL(12,3) NULL COMMENT '断奶-配种天数（断奶到配种天数之和/状态变化次数）',
  ADD COLUMN abnormal_total           INT           NULL DEFAULT 0 COMMENT '返空流总次数',
  ADD COLUMN avg_born_per_litter      DECIMAL(12,3) NULL COMMENT '窝均产仔数（总产仔之和/胎次）',
  ADD COLUMN avg_live_born_per_litter DECIMAL(12,3) NULL COMMENT '窝均活仔数（活产之和/胎次）',
  ADD COLUMN avg_weaned_per_litter    DECIMAL(12,3) NULL COMMENT '窝均断奶数（断奶头数之和/胎次）',
  ADD COLUMN npd                      DECIMAL(12,3) NULL COMMENT 'NPD（365−配种至分娩天数−分娩至断奶天数）';
