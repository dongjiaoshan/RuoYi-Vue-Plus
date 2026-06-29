-- BRD-STAT-FIX-001 — 年表 rename + T7 肥猪死亡数列（修 row14 D1/T7 blocker）。
--
-- D1：表名 t_farm_annual_indicator → t_farm_year_production（口径统一为「年生产表」）。
-- T7：年表加 total_fattening_death 列，upsertAnnualIndicator 从日表 Σ death_fattening_count 回读填入。
--
-- 注：之前迁移（V202605200901 建表 / V202605310900 补审计字段 / V202607262300 扩 row14 列）
--     建/改的是旧名 t_farm_annual_indicator；RENAME 把累计结构整体改名，新名沿用。
-- V202607280902 > V202607280901；裸 ALTER（业务表）。

SET NAMES utf8mb4;

-- 1. 表名 rename
RENAME TABLE t_farm_annual_indicator TO t_farm_year_production;

-- 2. T7 肥猪死亡数列
ALTER TABLE t_farm_year_production
  ADD COLUMN total_fattening_death INT NULL DEFAULT 0 COMMENT '肥猪死亡数（当年 Σ日 death_fattening_count）';
