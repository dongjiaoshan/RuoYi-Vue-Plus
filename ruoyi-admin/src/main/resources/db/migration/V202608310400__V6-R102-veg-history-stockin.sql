-- ============================================================================
-- V6 row102 毛菜处理间链路改造
--
-- 甲方口径：
--   1) 采摘录入后系统直接把作物化为产品，默认进毛菜保鲜库（L0006）；
--   2) 果蔬处理录入去掉【毛菜鲜品库】去向，剩下的「果蔬月台 / 有机饲喂」都要写毛菜保鲜库的出库记录；
--      点「地块处理完成」时，毛菜间剩下的库存记为损耗（记录方式与毛菜间损耗一致）；
--   3) 果蔬处理重量 = 带地块标识的产品从毛菜间出库的总重量；剩余重量 = 它在毛菜间的库存。
--
-- 本迁移做两件事：
--   1. 字典 djs_flow_type 新增 veg_stock_out（毛菜间出库）—— 上面第 2 条那条出库流水的类型。
--   2. 历史数据迁移（甲方第 5 点答复）：把「已称重完成、且还没做过任何处理」的地块，
--      按采摘重量补进毛菜保鲜库（建地块篮 + 补一条入库流水），让它们能走改造后的出库链路。
--      已经处理过的历史行一律不动 —— 甲方明确「历史记录按当时的口径原样展示，不回改」。
-- ============================================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 字典：djs_flow_type 新增 veg_stock_out
--    dict_code 续 102450029（third_phase_in）之后取 102450030。
--    ⚠️ 跑完必须刷 Redis 字典缓存：bash script/sql/djs/_post-init.sh
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type,
     css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (102450030, '1001', 30, '毛菜间出库', 'veg_stock_out', 'djs_flow_type',
     '', 'warning', 'N', 1, NOW(),
     'V6 row102 果蔬处理录入去向（果蔬月台 / 有机饲喂）从毛菜保鲜库 L0006 出库');

-- ------------------------------------------------------------
-- 2. 历史数据迁移
--
-- 目标行（四个条件缺一不可）：
--   · is_weighed = 1        已称重完成（甲方：已称重的数据）
--   · is_finish  <> 1       地块还没收口（收口过的行损耗已按当时口径结算完，再补库存就多出一批出不掉的货）
--   · handled_weight / feed_weight / stock_in_weight 三者全 0   还未进行任何操作
--   · picked_weight > 0     0 kg 收口记录没有实物，不建空篮
--
-- 产品解析链：handle.product_id → 作物产品配置首个 → 作物 related_product。
--   ⚠️ 三级都解析不到的行【直接跳过】（JOIN product_info 天然过滤）：没有产品就建不出可出库的库存篮，
--      与其造一条挂空 product 的孤儿库存行，不如让它保持现状。这类行需要人工在
--      admin 作物管理 → 编辑作物 →「产品配置」补齐产出产品后，用盘点 / 毛菜间出库为这批货补账。
--      核查 SQL 见文件末尾注释。
--
-- 幂等：以 stock_flow 上的 marker 备注判「这个 handle 已迁过」，重复执行不重复建篮。
--       两条 INSERT 共用同一张目标临时表，保证库存篮与入库流水永远成对出现。
-- ------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_r102_target;
CREATE TEMPORARY TABLE tmp_r102_target (
    handle_id     BIGINT        NOT NULL PRIMARY KEY,
    tenant_id     VARCHAR(20)   NOT NULL,
    location_id   BIGINT        NOT NULL,
    product_id    BIGINT        NOT NULL,
    plot_id       BIGINT        NOT NULL,
    product_name  VARCHAR(128)  NOT NULL,
    product_unit  VARCHAR(16)   NOT NULL,
    picked_weight DECIMAL(12, 3) NOT NULL,
    biz_time      DATETIME      NOT NULL,
    operator_id   BIGINT        NOT NULL,
    marker        VARCHAR(200)  NOT NULL
);

INSERT INTO tmp_r102_target
    (handle_id, tenant_id, location_id, product_id, plot_id,
     product_name, product_unit, picked_weight, biz_time, operator_id, marker)
SELECT h.id,
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
   AND h.is_weighed = 1
   AND h.is_finish <> 1
   AND h.plot_id IS NOT NULL
   AND COALESCE(h.picked_weight, 0)   > 0
   AND COALESCE(h.handled_weight, 0)  = 0
   AND COALESCE(h.feed_weight, 0)     = 0
   AND COALESCE(h.stock_in_weight, 0) = 0
   AND NOT EXISTS (SELECT 1
                     FROM t_warehouse_stock_flow f
                    WHERE f.remark = CONCAT('V6-R102 历史迁移入毛菜保鲜库 handleId=', h.id)
                      AND f.del_flag = '0');

-- 2.1 建地块篮（location_stock）
--     create_time 记业务时间而不是 NOW()：毛菜间按 FIFO 出库，篮子的先后顺序按 create_time 排，
--     全写 NOW() 会让这批最老的货排到新采摘的货后面去。
INSERT INTO t_warehouse_location_stock
    (tenant_id, location_id, product_id, plot_id, product_name, product_stock, product_unit,
     is_end, third_phase, operator_id, create_by, create_time, del_flag, del_unique, remark)
SELECT t.tenant_id, t.location_id, t.product_id, t.plot_id, t.product_name, t.picked_weight, t.product_unit,
       0, 0, t.operator_id, 1, t.biz_time, '0', 0, t.marker
  FROM tmp_r102_target t;

-- 2.2 补入库流水（stock_flow）
--     flow_no 用 'FMIG' + handle_id：handle_id 唯一 → 单号唯一（uk_flow_no），且一眼看得出是迁移补的。
INSERT INTO t_warehouse_stock_flow
    (tenant_id, flow_no, flow_date, product_id, warehouse_id, inout_type, flow_type,
     change_num, change_quantity, plot_id, third_phase, operator_id,
     create_by, create_time, del_flag, del_unique, remark)
SELECT t.tenant_id, CONCAT('FMIG', t.handle_id), t.biz_time, t.product_id, t.location_id, 'IN', 'veg_stock_in',
       t.picked_weight, t.picked_weight, t.plot_id, 0, t.operator_id,
       1, NOW(), '0', 0, t.marker
  FROM tmp_r102_target t;

DROP TEMPORARY TABLE tmp_r102_target;

-- ------------------------------------------------------------
-- 核查（人工跑，不属于迁移）：
--
-- ① 本次迁移了多少行 / 多少 kg
--    SELECT COUNT(*), SUM(change_num) FROM t_warehouse_stock_flow
--     WHERE remark LIKE 'V6-R102 历史迁移入毛菜保鲜库 handleId=%' AND del_flag='0';
--
-- ② 因为解析不到产品被跳过、需要人工补作物产品配置的地块
--    SELECT h.id AS handle_id, h.plot_id, h.crop_id, h.picked_weight
--      FROM t_warehouse_vegetable_handle h
--     WHERE h.del_flag='0' AND h.is_weighed=1 AND h.is_finish<>1 AND h.plot_id IS NOT NULL
--       AND COALESCE(h.picked_weight,0) > 0
--       AND COALESCE(h.handled_weight,0)=0 AND COALESCE(h.feed_weight,0)=0
--       AND COALESCE(h.stock_in_weight,0)=0
--       AND NOT EXISTS (SELECT 1 FROM t_warehouse_stock_flow f
--                        WHERE f.remark = CONCAT('V6-R102 历史迁移入毛菜保鲜库 handleId=', h.id)
--                          AND f.del_flag='0');
-- ------------------------------------------------------------
