-- ============================================================
-- DENGBO-R206 仓库日指标：合并「果蔬生产损耗」为唯一值，删除 prod_consume_weight
-- ============================================================
-- 邓博 2026-07-06 最终口径：仓库聚合日表「只需要果蔬的生产损耗」一个值。
--   prod_loss_weight 改算 = max(0, 果蔬领用 − 退回 − 录入损耗 − 饲喂(feed_out) − 打包生产使用量)，
--   全部按 belong_type='vegetable'（自产果蔬；外购商品/包材天然排除，符合「只算产品的」）。
--   原独立列 prod_consume_weight 不再需要（不需要两个值），本迁移删除该列。
--   「总生产损耗（全部产品）」按客户口径记在损耗总览表，不在本聚合日表展示。
-- 幂等 guard（INFORMATION_SCHEMA 守卫）：可先手动 apply staging，Flyway 部署重跑安全。
-- version 202607312300 > 当前 flyway max V202607312200。仅删列 + 改注释，无字典/无 redis 影响。
-- 历史值由重跑聚合 job（POST /djs/warehouse/stat/trigger-aggregate?date=）回填。
-- ============================================================
SET NAMES utf8mb4;
SET @db := DATABASE();

-- 删除不再需要的 prod_consume_weight（存在才删）
SET @has_pc := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_warehouse_indicator_record' AND COLUMN_NAME='prod_consume_weight');
SET @sql := IF(@has_pc>0,
  "ALTER TABLE t_warehouse_indicator_record DROP COLUMN prod_consume_weight",
  "DO 0");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- prod_loss_weight 注释同步为最终口径（存在才改）
SET @has_pl := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_warehouse_indicator_record' AND COLUMN_NAME='prod_loss_weight');
SET @sql2 := IF(@has_pl>0,
  "ALTER TABLE t_warehouse_indicator_record MODIFY COLUMN prod_loss_weight DECIMAL(12,3) NULL COMMENT '果蔬生产损耗总重（当日果蔬领用−退回−录入损耗−饲喂(feed_out)−打包生产使用量，≥0；仅自产果蔬）'",
  "DO 0");
PREPARE s2 FROM @sql2; EXECUTE s2; DEALLOCATE PREPARE s2;
