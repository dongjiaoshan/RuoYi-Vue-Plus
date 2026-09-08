-- 仓库日指标：白条均重的分母列（当日入白条库的猪只耳号去重数）。
--
-- 白条均重 = 白条总重 / 当日入白条库的猪只耳号数量（去重）。日表只落最终均值的话，
-- 矩阵「累计」列无法按 Σ分子/Σ分母 还原（只能拿日均值再平均，各天头数不同 → 失真），
-- 故把分母单独落一列，与 slaughter_rate_* / bar_yield_* 那几组 cohort 基数列同一个做法。
--
-- 不复用 finished_count：那一列是「当日 bar.finish_time 落当天的猪只头数」，与本列
-- 「当日入白条库的猪只数」是两批猪（同一头猪可以 A 日入库、B 日才点处理完成），
-- 且它已降为诊断列，再改一次语义就是在同一个字段上叠第三层含义。

SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_warehouse_indicator_record'
               AND COLUMN_NAME = 'bar_pig_count');
SET @s1 := IF(@c1 > 0, 'SELECT 1',
  "ALTER TABLE t_warehouse_indicator_record
     ADD COLUMN bar_pig_count INT NOT NULL DEFAULT 0
     COMMENT '白条均重分母：当日入白条库的猪只耳号去重数（一头猪出两扇只算 1 头）' AFTER bar_total_weight");
PREPARE st1 FROM @s1;
EXECUTE st1;
DEALLOCATE PREPARE st1;

-- 新列首次加入时按口径回填存量日表行（列已存在 = 重跑，整段跳过）。
-- 取数与 WarehouseStatAggregateMapper#selectWhiteBarInAgg 逐字同口径：
-- 燎毛产出入库流水（不可变、可复现）× 白条产品类别，自养按耳号去重、外购无耳号退白条 id。
-- 这里只填新列，不动 bar_total_weight / bar_yield_* 等已落库的既有值 —— 历史日按新口径重算
-- 走 POST /djs/warehouse/stat/trigger-aggregate?date=yyyy-MM-dd，是另一个决定。
SET @bf := IF(@c1 = 0,
  "UPDATE t_warehouse_indicator_record r
      SET r.bar_pig_count = (
            SELECT COUNT(DISTINCT COALESCE(f.ear_no,
                     CONCAT('bar:', (SELECT ih.white_bar_id
                                       FROM t_warehouse_product_inhouse ih
                                      WHERE ih.white_bar_no = f.white_bar_no
                                        AND ih.tenant_id = f.tenant_id
                                      ORDER BY ih.id LIMIT 1)),
                     CONCAT('half:', f.white_bar_no)))
              FROM t_warehouse_stock_flow f
              JOIN t_warehouse_product_info p
                ON p.id = f.product_id AND p.tenant_id = f.tenant_id
             WHERE f.del_flag = '0'
               AND f.tenant_id = r.tenant_id
               AND f.inout_type = 'IN'
               AND f.flow_type = 'slaughter_burn'
               AND p.belong_type = 'white_bar'
               AND DATE(f.flow_date) = r.stat_date)",
  'SELECT 1');
PREPARE stbf FROM @bf;
EXECUTE stbf;
DEALLOCATE PREPARE stbf;

-- 白条入库聚合逐日按 (flow_date, flow_type, inout_type) 扫流水表，补分桶索引。
SET @c2 := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_warehouse_stock_flow'
               AND INDEX_NAME = 'idx_flow_type_date');
SET @s2 := IF(@c2 > 0, 'SELECT 1',
  'CREATE INDEX idx_flow_type_date ON t_warehouse_stock_flow (tenant_id, flow_type, inout_type, flow_date)');
PREPARE st2 FROM @s2;
EXECUTE st2;
DEALLOCATE PREPARE st2;
