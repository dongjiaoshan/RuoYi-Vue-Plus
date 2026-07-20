-- ============================================================
-- DENGBO-R206 仓库日指标：新增「果蔬生产消耗总重」prod_consume_weight
-- ============================================================
-- 邓博 2026-07-06（admin 测试表 row206）：仓库统计聚合定时任务未计算「果蔬生产消耗总重」。
--   本次仅新增独立指标 prod_consume_weight，不改动现有「生产损耗」等其他列（各算各的、互不牵扯）。
--   口径 = 果蔬产品领用 − 退回 − 录入损耗(manual_loss) − 饲喂，均按 belong_type='vegetable' 过滤，残差 ≥0；
--   其中饲喂取 stock_flow feed_out（从生产领用池出库、天然 ≤ 领用），非 feed_log 的毛菜间/仓库饲喂。
--   （领用/退回/feed_out 与邓博 row38 ProductionLossAggregateMapper 同源同口径。）
-- 幂等 guard（INFORMATION_SCHEMA 守卫）：可先手动 apply staging 做 live 验证，Flyway 部署重跑安全。
-- version 202607312200 > 当前 flyway max V202607312100。列纯新增，无字典/无 redis 影响。
-- 历史值由重跑聚合 job（POST /djs/warehouse/stat/trigger-aggregate?date=）回填。
-- ============================================================
SET NAMES utf8mb4;
SET @db := DATABASE();

SET @has_pc := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_warehouse_indicator_record' AND COLUMN_NAME='prod_consume_weight');
SET @sql := IF(@has_pc=0,
  "ALTER TABLE t_warehouse_indicator_record ADD COLUMN prod_consume_weight DECIMAL(12,3) NULL COMMENT '果蔬生产消耗总重（果蔬领用−退回−录入损耗−饲喂，≥0）' AFTER prod_loss_weight",
  "DO 0");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
