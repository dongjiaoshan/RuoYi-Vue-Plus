-- 出品率分子分母两列的注释对齐甲方 2026-09-07 定的口径（V6 row172）。
--
-- 甲方把需求原文里这一句从
--   「白条出品率：白条总重 / 完成处理的猪只接收重量之和」
-- 改成
--   「白条出品率：白条总重 / 完成接收重量的猪只出栏重量之和」
-- ——与上一行屠宰率的分母逐字相同，答复里也写明「分母错误，也是【完成接收重量的猪只出栏重量之和】」。
-- 于是两个比率共用同一个分母：称重 cohort ∩ 出栏重量非空 的 Σ出栏重量，
-- 即 bar_yield_base_weight ≡ slaughter_rate_base_weight，bar_yield_numer_weight ≡ bar_total_weight。
--
-- 两列保留（不删）：月表与年度看板按 Σ分子 ÷ Σ分母 重算比率，需要逐日落盘的分子分母。
--
-- ⚠️ 由此分子（处理完成 cohort）与分母（称重 cohort）不是同一批猪，出品率可能 >100%。
-- 该后果已在写回中明确告知甲方，甲方知情后仍指定本口径。
--
-- 存量行不在此回填：口径变更后需要整段重算日表（trigger-aggregate 按日 UPSERT 幂等），
-- 回填公式再写一遍等于把同一口径实现两遍，只会与聚合代码漂移。
-- MODIFY 只改注释，天然幂等。

ALTER TABLE t_warehouse_indicator_record
  MODIFY COLUMN bar_yield_numer_weight DECIMAL(12,3) NOT NULL DEFAULT 0.000
    COMMENT '白条出品率分子：当日处理完成猪只的白条重之和（≡ bar_total_weight）',
  MODIFY COLUMN bar_yield_base_weight DECIMAL(12,3) NOT NULL DEFAULT 0.000
    COMMENT '白条出品率分母：完成接收重量的猪只出栏重量之和（与屠宰率共用，≡ slaughter_rate_base_weight）',
  MODIFY COLUMN finished_arrive_weight DECIMAL(12,3) NOT NULL DEFAULT 0.000
    COMMENT '当日处理完成的猪只，接收重量之和（诊断列，不参与任何比率）',
  MODIFY COLUMN bar_yield_rate DECIMAL(12,3) DEFAULT NULL
    COMMENT '白条出品率%（白条总重/完成接收重量的猪只出栏重量之和×100；分母 0 → NULL。分子分母跨 cohort，可能>100%）';
