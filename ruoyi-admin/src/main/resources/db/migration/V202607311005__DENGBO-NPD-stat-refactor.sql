-- DENGBO-NPD-stat-refactor — 养殖 NPD 口径重构（邓博 2026-07-02 确认口径，admin 测试表 row112-115）。
--
--   row112 日表：新增 npd_days（当日非生产状态母猪头数） + feed_total_days（饲养总天数 Σ 出栏日−断奶日+1）；
--                growth_total_days 复用现列，语义改为「Σ 当日出栏猪 出栏日−出生日+1」（依赖出生日期，外购猪跳过）。
--   row113 母猪性能 NPD：算法整体重写为「母猪历史非生产区间天数总和」（本迁移无列变更，纯 service 改）。
--   row114 月表：新增 avg_prod_sow_stock（月均生产母猪存栏 Σ日期末生产母猪/当月已历天数）；
--                npd_days 语义改为「Σ日非生产母猪 / avg_prod_sow_stock」。
--   row115 年表：avg_breeding_sow_stock → avg_prod_sow_stock（去掉 230 后备，仅纯生产母猪）；
--                total_npd_days 语义改为「Σ日非生产母猪」（去 230 后备）；物理删 breed_rate 列（年配种率下线）。
--
-- 全部 ALTER ADD/CHANGE/DROP COLUMN（项目主流，非 IF NOT EXISTS）；头数类 INT，重量/天数/率类 DECIMAL(12,3)。
-- V202607311005 > 当前 flyway max V202607311004。

SET NAMES utf8mb4;

-- ============================================================
-- 1. t_farm_indicator_record（row112 日表）
-- ============================================================
ALTER TABLE t_farm_indicator_record
  ADD COLUMN npd_days        INT NULL DEFAULT 0 COMMENT '日NPD天数（当日非生产状态母猪头数 = end_nonprod_sow_count）',
  ADD COLUMN feed_total_days INT NULL DEFAULT 0 COMMENT '饲养总天数（Σ当日出栏猪 出栏日−断奶日+1）';

-- ============================================================
-- 2. t_farm_monthly_production（row114 月表）
-- ============================================================
ALTER TABLE t_farm_monthly_production
  ADD COLUMN avg_prod_sow_stock DECIMAL(12,3) NULL DEFAULT 0 COMMENT '月均生产母猪存栏数（Σ日期末生产母猪头数/当月已历天数）';

-- ============================================================
-- 3. t_farm_year_production（row115 年表）
-- ============================================================
ALTER TABLE t_farm_year_production
  CHANGE COLUMN avg_breeding_sow_stock avg_prod_sow_stock DECIMAL(12,3) NULL DEFAULT 0 COMMENT '年均生产母猪存栏数（Σ日期末生产母猪头数/已历天数）';

ALTER TABLE t_farm_year_production
  DROP COLUMN breed_rate;
