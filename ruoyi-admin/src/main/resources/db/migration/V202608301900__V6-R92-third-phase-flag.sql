-- ============================================================================
-- V6 row92：【三期】标识落到库存行 / 流水行上（入库记录 · 出库记录 · 库存查询 三处都要显示）
--
-- 甲方口径：「三期」目前**没有真实地块**，只做文案显示；要能以三期标识入库 / 出库，
--   并按三期标识统计总入库 / 总出库。
--
-- 为什么是「加一个布尔标记」而不是「建一个虚拟三期地块」：
--   t_warehouse_location_stock.plot_id 是承重字段 —— VegOutServiceImpl 用
--   (产品→作物, 库存行 plot_id) 去 t_warehouse_vegetable_handle 定位行累加 send_platform_weight
--   （果蔬月台待入库量的唯一口径）。造一个假的「三期」地块会让这个定位落空 →
--   走 createMinimalHandle 补建行 → 污染毛菜处理数据与月台账。
--   只加 third_phase 标记、plot_id 一个字节都不动 → 零功能影响。
--
-- 本文件三件事：
--   1. t_warehouse_stock_flow  加 third_phase + 索引
--   2. t_warehouse_location_stock 加 third_phase + 索引
--   3. 回填存量：已有的 third_phase_in 流水置 1，其对应的库存篮置 1
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 幂等守卫：MySQL 的 ADD COLUMN / CREATE INDEX 没有 IF NOT EXISTS，裸写在迁移半途失败后
-- 重跑会直接 Duplicate column(1060) / Duplicate key name(1061)。统一用 information_schema
-- 探测 + PREPARE/EXECUTE 动态 SQL（写法同 V202608301700）。
--
-- 两条 ADD COLUMN 都**不带 AFTER**：MySQL 8.0.12~8.0.28 的 ALGORITHM=INSTANT 只在把列追加到
-- 表尾时可用，带 AFTER 会退化成 INPLACE 重建整表。stock_flow 是全库最大的一张表，
-- 列的物理位置对 ORM 毫无影响，不值得为排版好看去锁表。
-- ----------------------------------------------------------------------------

-- 1. 出入库流水
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = 't_warehouse_stock_flow'
                       AND COLUMN_NAME = 'third_phase');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE t_warehouse_stock_flow ADD COLUMN third_phase TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''三期标识 0否 1是（1=这笔出入库算在【三期】名下，地块列渲染成「三期」）''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = 't_warehouse_stock_flow'
                       AND INDEX_NAME = 'idx_flow_third_phase');
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_flow_third_phase ON t_warehouse_stock_flow (tenant_id, third_phase)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 库存明细
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = 't_warehouse_location_stock'
                       AND COLUMN_NAME = 'third_phase');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE t_warehouse_location_stock ADD COLUMN third_phase TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''三期标识 0否 1是（1=三期货篮，与普通货分篮不混账；地块列渲染成「三期」）''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = 't_warehouse_location_stock'
                       AND INDEX_NAME = 'idx_stock_third_phase');
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_stock_third_phase ON t_warehouse_location_stock (tenant_id, third_phase)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 3. 回填存量
--    3.1 已有的三期作物入库流水直接置 1（flow_type 就是判据，不用猜）。
--    3.2 这些流水**当场建出来的那一行**库存篮置 1。见下方长注释。
--    重跑安全：两条都是「置 1」的幂等 UPDATE。
-- ----------------------------------------------------------------------------
UPDATE t_warehouse_stock_flow
   SET third_phase = 1
 WHERE flow_type = 'third_phase_in'
   AND third_phase <> 1;

-- ----------------------------------------------------------------------------
-- 3.2 存量三期库存篮回填 —— 判据是「这一行是不是这笔三期流水当场 INSERT 出来的」
--
-- 为什么不能只按 (warehouse_id, product_id, plot_id) 反查：那三个键**不是**库存篮的唯一键。
--   · 三期入库在「作物恰好 1 块在种地块」时会建带真实 plot_id 的篮，与采摘入库的普通地块篮
--     形状完全相同、还常落同一个库位（毛菜鲜品库 L0006）；
--   · 无地块时建的产品级篮（plot/ear/white_bar 全 NULL），与退货篮 / 普通产品级篮也同形状。
--   两种情况下同一组键都可能对应多行，一条无 LIMIT 的 UPDATE 会把同形状的兄弟行一起置 1 ——
--   普通采摘的货凭空变成三期，比不回填坏得多。
--
-- 能确定归属的只有一件事：**库存行与流水行是同一个事务里前后脚 INSERT 的**
-- （ThirdPhaseInServiceImpl#insertStockIn 先写 flow 再写 basket），两者 create_time 落在同一秒。
--   · 两列都是 DATETIME 无小数秒，跨秒边界会差 1 → 容差取 ±1 秒；
--   · create_time 为 NULL（手工塞的种子数据）时 TIMESTAMPDIFF 返 NULL → 条件不成立 → 跳过，正确；
--   · 旧版本三期入库若是**累加进一条早就存在的普通篮**（UPDATE 而非 INSERT），该篮 create_time
--     远早于流水 → 被时间条件排除 → **不回填**。这正是要的结果：那条篮里普通货和三期货已经混在
--     一起、再也拆不开，给它打三期标识等于把普通货也标成三期。少回填，不误伤。
--
-- 还要再挡一道：万一同一秒里同组键真的诞生了 2 行（并发入库），谁是谁就判不了了 ——
-- 这种流水直接整条跳过（HAVING COUNT(DISTINCT s.id) = 1）。
-- 判不了归属的一律不回填，让它留在 third_phase=0，宁可漏也不错标。
--
-- 用临时表两步走而不是一条 UPDATE：「同组键只有一行候选」这个条件要 COUNT 目标表自身，
-- MySQL 不允许在 UPDATE 的子查询里再读被更新的表（ERR 1093），只能先把定位结果落到临时表。
-- 临时表是 session 级的，迁移跑完即消失，不留物。
-- ----------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_r92_third_phase_stock;
CREATE TEMPORARY TABLE tmp_r92_third_phase_stock (
    stock_id BIGINT NOT NULL PRIMARY KEY
);

INSERT INTO tmp_r92_third_phase_stock (stock_id)
SELECT DISTINCT d.stock_id
  FROM (
        SELECT MIN(s.id) AS stock_id
          FROM t_warehouse_location_stock s
          JOIN t_warehouse_stock_flow f
            ON f.warehouse_id = s.location_id
           AND f.product_id   = s.product_id
           AND f.plot_id     <=> s.plot_id
           AND f.tenant_id    = s.tenant_id
           AND f.del_flag     = '0'
           AND ABS(TIMESTAMPDIFF(SECOND, s.create_time, f.create_time)) <= 1
          JOIN t_warehouse_third_phase_in t
            ON t.flow_id  = f.id
           AND t.del_flag = '0'
         WHERE s.del_flag      = '0'
           AND s.ear_no       IS NULL
           AND s.white_bar_no IS NULL
         GROUP BY f.id
        HAVING COUNT(DISTINCT s.id) = 1
       ) d;

UPDATE t_warehouse_location_stock s
  JOIN tmp_r92_third_phase_stock m
    ON m.stock_id = s.id
   SET s.third_phase = 1
 WHERE s.third_phase <> 1;

DROP TEMPORARY TABLE IF EXISTS tmp_r92_third_phase_stock;
