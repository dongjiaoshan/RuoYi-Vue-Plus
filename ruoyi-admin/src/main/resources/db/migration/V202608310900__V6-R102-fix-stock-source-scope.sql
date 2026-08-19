-- ============================================================================
-- V6 row102 修复：给毛菜保鲜库的「地块篮」加来源维度 source_biz_id
--
-- 为什么要加这一列：
--   改造第一版把篮子的定位键定为 (location_id, product_id, plot_id) —— 这等于假设
--   「一个地块同一时刻只有一条业务流」。真实数据里同一 (地块, 产品) 会同时存在：
--     ① 同地块同作物的两条 t_warehouse_planting_record（两季 / 补录）；
--     ② 采摘活动 pick_dest=veg_fresh 直送毛菜保鲜室放进来的货（无 planting_record）；
--     ③ 两个不同作物共享同一产品时各自的货。
--   于是「地块处理完成结转损耗」「处理录入 FIFO 扣减」「地块卡剩余重量」三处全部会串到别人的货上
--   （实测：关闭 A 记录把 B 记录的 25kg 结成了 A 的损耗，loss > picked）。
--
--   加一列「这篮是谁建的」，三处读写按它收窄，串货的物理可能性直接消失。
--
-- 列语义：
--   source_biz_id = t_warehouse_planting_record.id（毛菜采摘录入建的地块篮）；
--   其余链路（白条篮 / 三期篮 / 月台收货篮 / 退货篮 / 采摘活动直送篮）一律留空，不受影响。
--   留空的篮子对毛菜处理链路不可见 —— 它们的出库入口是「毛菜间出库管理」（按行 id 出），本就不该
--   被某条种植记录的处理录入 / 收口损耗吃掉。
--
-- 本文件三件事：
--   1. t_warehouse_location_stock 加列 source_biz_id + 索引（幂等）
--   2. 回填 V202608310400 补建的历史篮的 source_biz_id
--   3. 补建 V202608310400 漏掉的那批篮：它要求 is_weighed = 1，把「采摘还在进行中
--      （is_weighed=2）但已经称了重」的地块整批漏掉了，那些 picked_weight 拿不到任何库存、
--      收口时静默蒸发（实测 40kg）。这里按同一个 marker 幂等补建，已迁过的不会重复建。
--
-- ⚠️ V202608310400 已在环境上应用过，直接改它会 checksum 不匹配导致启动失败 —— 补漏逻辑只能写在这里。
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. 加列 + 索引
--    MySQL 的 ADD COLUMN / CREATE INDEX 没有 IF NOT EXISTS，裸写在迁移半途失败后重跑会
--    Duplicate column(1060) / Duplicate key name(1061)。统一用 information_schema 探测 +
--    PREPARE/EXECUTE（写法同 V202608301900）。
--    ADD COLUMN 不带 AFTER：带 AFTER 会让 MySQL 8 的 ALGORITHM=INSTANT 退化成 INPLACE 重建整表。
-- ----------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = 't_warehouse_location_stock'
                       AND COLUMN_NAME = 'source_biz_id');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE t_warehouse_location_stock ADD COLUMN source_biz_id BIGINT NULL COMMENT ''来源业务 id（毛菜地块篮 = t_warehouse_planting_record.id；其余链路为空）''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = 't_warehouse_location_stock'
                       AND INDEX_NAME = 'idx_stock_source_biz');
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_stock_source_biz ON t_warehouse_location_stock (tenant_id, source_biz_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 2. 回填 V202608310400 补建的历史篮
--
--    那批篮的 remark 就是 marker（'V6-R102 历史迁移入毛菜保鲜库 handleId=<handle.id>'），
--    handle → planting_record_id 即本篮的来源。
--    幂等：只填 source_biz_id IS NULL 的行；重复执行第二次命中 0 行。
-- ----------------------------------------------------------------------------
UPDATE t_warehouse_location_stock s
  JOIN t_warehouse_vegetable_handle h
    ON s.remark = CONCAT('V6-R102 历史迁移入毛菜保鲜库 handleId=', h.id)
   AND h.del_flag  = '0'
   AND h.tenant_id = s.tenant_id
   SET s.source_biz_id = h.planting_record_id
 WHERE s.del_flag = '0'
   AND s.source_biz_id IS NULL
   AND h.planting_record_id IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 3. 补建 V202608310400 漏掉的篮（放宽 is_weighed 条件）
--
--    目标行（与 V202608310400 逐条对齐，唯一差别是**去掉 is_weighed = 1**）：
--      · is_finish  <> 1       地块还没收口（收过口的行损耗已按当时口径结算完，再补库存就多出一批出不掉的货）
--      · handled_weight / feed_weight / stock_in_weight 三者全 0   还未进行任何操作
--      · picked_weight > 0     0 kg 收口记录没有实物，不建空篮
--      · planting_record_id 非空  没有它就填不出 source_biz_id，也就无法被处理录入定位到
--
--    去掉 is_weighed = 1 的理由：称重完成只是「不再新增采摘录入」的开关，与「这批货在不在毛菜间」
--    毫无关系。采摘还在进行中的地块（is_weighed=2 但 picked_weight > 0）同样有实物躺在毛菜间，
--    第一版把它们排除掉 → 处理录入一律报库存不足、收口时那批 kg 静默蒸发。
--
--    幂等：沿用 V202608310400 的同一个 marker 判重，0400 已迁过的行天然跳过、本文件只补它漏掉的那批。
--    产品解析链与 0400 完全一致：handle.product_id → 作物产品配置首个 → 作物 related_product；
--    三级都解析不到的行直接跳过（JOIN product_info 天然过滤），需人工补作物产品配置后用盘点补账。
-- ----------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_r102fix_target;
CREATE TEMPORARY TABLE tmp_r102fix_target (
    handle_id         BIGINT         NOT NULL PRIMARY KEY,
    planting_record_id BIGINT        NOT NULL,
    tenant_id         VARCHAR(20)    NOT NULL,
    location_id       BIGINT         NOT NULL,
    product_id        BIGINT         NOT NULL,
    plot_id           BIGINT         NOT NULL,
    product_name      VARCHAR(128)   NOT NULL,
    product_unit      VARCHAR(16)    NOT NULL,
    picked_weight     DECIMAL(12, 3) NOT NULL,
    biz_time          DATETIME       NOT NULL,
    operator_id       BIGINT         NOT NULL,
    marker            VARCHAR(200)   NOT NULL
);

INSERT INTO tmp_r102fix_target
    (handle_id, planting_record_id, tenant_id, location_id, product_id, plot_id,
     product_name, product_unit, picked_weight, biz_time, operator_id, marker)
SELECT h.id,
       h.planting_record_id,
       h.tenant_id,
       loc.id,
       pi.id,
       h.plot_id,
       pi.product_name,
       COALESCE(NULLIF(pi.product_unit, ''), 'kg'),
       h.picked_weight,
       COALESCE(h.pick_end_time, h.update_time, h.create_time, NOW()),
       COALESCE(h.update_by, h.create_by, 1),
       CONCAT('V6-R102 历史迁移入毛菜保鲜库 handleId=', h.id)
  FROM t_warehouse_vegetable_handle h
  JOIN t_warehouse_location_info loc
    ON loc.location_code = 'L0006'
   AND loc.del_flag  = '0'
   AND loc.tenant_id = h.tenant_id
  JOIN t_warehouse_product_info pi
    ON pi.id = COALESCE(
                 h.product_id,
                 (SELECT cp.product_id
                    FROM t_plant_crop_product cp
                   WHERE cp.crop_id   = h.crop_id
                     AND cp.del_flag  = '0'
                     AND cp.tenant_id = h.tenant_id
                   ORDER BY cp.id
                   LIMIT 1),
                 (SELECT c.related_product
                    FROM t_plant_crop_info c
                   WHERE c.id = h.crop_id
                     AND c.del_flag = '0'))
   AND pi.del_flag = '0'
 WHERE h.del_flag   = '0'
   AND h.is_finish <> 1
   AND h.plot_id IS NOT NULL
   AND h.planting_record_id IS NOT NULL
   AND COALESCE(h.picked_weight, 0)   > 0
   AND COALESCE(h.handled_weight, 0)  = 0
   AND COALESCE(h.feed_weight, 0)     = 0
   AND COALESCE(h.stock_in_weight, 0) = 0
   AND NOT EXISTS (SELECT 1
                     FROM t_warehouse_stock_flow f
                    WHERE f.remark = CONCAT('V6-R102 历史迁移入毛菜保鲜库 handleId=', h.id)
                      AND f.del_flag = '0');

-- 3.1 建地块篮（带 source_biz_id）
--     create_time 记业务时间而不是 NOW()：毛菜间按 FIFO 出库、篮子按 create_time 排，
--     全写 NOW() 会让这批最老的货排到新采摘的货后面去。
INSERT INTO t_warehouse_location_stock
    (tenant_id, location_id, product_id, plot_id, source_biz_id, product_name, product_stock, product_unit,
     is_end, third_phase, operator_id, create_by, create_time, del_flag, del_unique, remark)
SELECT t.tenant_id, t.location_id, t.product_id, t.plot_id, t.planting_record_id,
       t.product_name, t.picked_weight, t.product_unit,
       0, 0, t.operator_id, 1, t.biz_time, '0', 0, t.marker
  FROM tmp_r102fix_target t;

-- 3.2 补入库流水
--     flow_no 用 'FMIG' + handle_id：handle_id 唯一 → 单号唯一（uk_flow_no），且一眼看得出是迁移补的。
INSERT INTO t_warehouse_stock_flow
    (tenant_id, flow_no, flow_date, product_id, warehouse_id, inout_type, flow_type,
     change_num, change_quantity, plot_id, third_phase, operator_id,
     create_by, create_time, del_flag, del_unique, remark)
SELECT t.tenant_id, CONCAT('FMIG', t.handle_id), t.biz_time, t.product_id, t.location_id, 'IN', 'veg_stock_in',
       t.picked_weight, t.picked_weight, t.plot_id, 0, t.operator_id,
       1, NOW(), '0', 0, t.marker
  FROM tmp_r102fix_target t;

DROP TEMPORARY TABLE tmp_r102fix_target;

-- ----------------------------------------------------------------------------
-- 核查（人工跑，不属于迁移）：
--
-- ① 还有多少毛菜地块篮没有来源标识（应只剩「采摘活动直送毛菜保鲜室」建的篮）
--    SELECT COUNT(*) FROM t_warehouse_location_stock s
--      JOIN t_warehouse_location_info li ON li.id = s.location_id AND li.location_code = 'L0006'
--     WHERE s.del_flag='0' AND s.product_id IS NOT NULL AND s.plot_id IS NOT NULL
--       AND s.third_phase = 0 AND s.source_biz_id IS NULL;
--
-- ② 本轮补建了多少行（3 段新建的，marker 与 0400 同款，按 create_time 区分）
--    SELECT COUNT(*), SUM(change_num) FROM t_warehouse_stock_flow
--     WHERE remark LIKE 'V6-R102 历史迁移入毛菜保鲜库 handleId=%' AND del_flag='0';
--
-- ③ 仍因解析不到产品被跳过、需要人工补作物产品配置的地块
--    SELECT h.id AS handle_id, h.planting_record_id, h.plot_id, h.crop_id, h.picked_weight
--      FROM t_warehouse_vegetable_handle h
--     WHERE h.del_flag='0' AND h.is_finish<>1 AND h.plot_id IS NOT NULL
--       AND h.planting_record_id IS NOT NULL
--       AND COALESCE(h.picked_weight,0) > 0
--       AND COALESCE(h.handled_weight,0)=0 AND COALESCE(h.feed_weight,0)=0
--       AND COALESCE(h.stock_in_weight,0)=0
--       AND NOT EXISTS (SELECT 1 FROM t_warehouse_stock_flow f
--                        WHERE f.remark = CONCAT('V6-R102 历史迁移入毛菜保鲜库 handleId=', h.id)
--                          AND f.del_flag='0');
-- ----------------------------------------------------------------------------
