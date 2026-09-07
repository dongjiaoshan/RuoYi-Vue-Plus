-- 产出记录落「本次抵扣需求量」，到店量由数条数改成按量求和（V6 row161）。
--
-- 旧口径「到店量 = 绑到该需求的产出记录 COUNT(*)」在两类产品上会少算，把已全额送到的需求
-- 误判成「部分到店」：
--   · 礼盒：一次打包只落 1 条记录，却按 packBoxCount 抵 N 盒需求；
--   · KG 计量的猪肉 / 干货：一次称重也只落 1 条记录，却把整行 kg 需求扣满。
-- 新列 demand_deduct_qty = 这条记录抵多少需求量（按需求单位计），到店量 = Σ 本列。

SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_warehouse_product_production'
               AND COLUMN_NAME = 'demand_deduct_qty');
SET @s1 := IF(@c1 > 0, 'SELECT 1',
  "ALTER TABLE t_warehouse_product_production
     ADD COLUMN demand_deduct_qty DECIMAL(12,3) NOT NULL DEFAULT 1.000
     COMMENT '本条产出记录抵多少门店需求量（按需求单位）：礼盒=盒数 / KG 计量=本条重量kg / 其余计件单位=1。到店量 = Σ 本列' AFTER produce_quantity");
PREPARE st1 FROM @s1;
EXECUTE st1;
DEALLOCATE PREPARE st1;

-- 存量行按「这条记录当初实际抵了多少」回填，让历史需求的到店量一次性算对
-- （不是保留旧的错值——甲方正是拿存量数据复现的这条问题）：
--   · 礼盒行：produce_quantity 落的就是 packBoxCount，直接用；
--   · KG 计量行：produce_quantity 落的是本条 consume kg，也直接用；
--   · 其余计件行（份 / 枚 / 头 …）：打包时已按件数拆成对应条数，一条抵一件 → 1，
--     与旧的 COUNT(*) 完全等价，这部分历史数字不会变。
-- 判礼盒只认 belong_type='gift_box'（djs_product_type 的 3=礼盒 已废弃）；判 KG 只认 kg / 公斤，
-- 与 ProductProductionServiceImpl.isKgUnit 同口径。
-- 幂等守卫按列自己判：@c1 = 0 = 本次才加的列 = 首次执行才回填；重跑整段跳过，
-- 不能按「= 1」判存量——一条合法的新计件行天然就是 1，第二遍会被误当存量行改写。
SET @bf := IF(@c1 = 0,
  "UPDATE t_warehouse_product_production pp
     LEFT JOIN t_warehouse_product_info pi
            ON pi.id = pp.product_id AND pi.del_flag = '0' AND pi.tenant_id = pp.tenant_id
      SET pp.demand_deduct_qty = CASE
            WHEN pi.belong_type = 'gift_box'
              THEN COALESCE(NULLIF(pp.produce_quantity, 0), NULLIF(pp.product_weight, 0), 1)
            WHEN LOWER(TRIM(pp.product_unit)) IN ('kg', '公斤')
              THEN COALESCE(NULLIF(pp.produce_quantity, 0), NULLIF(pp.product_weight, 0), 0)
            ELSE 1
          END",
  'SELECT 1');
PREPARE stbf FROM @bf;
EXECUTE stbf;
DEALLOCATE PREPARE stbf;
