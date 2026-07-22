-- FIX-ADMIN-0722（邓博 row17）：历史白条领用残量清零 + 补损耗出库流水
-- 根因：三条白条出库路径（分割领用 / 发货月台 / 仓库出库）只按领用过磅重扣白条篮，
-- 篮内残量（入库重 − 领用重）永久留在 t_warehouse_location_stock 成死库存（每个半只篮
-- 只能领用一次，inhouse.pickup_status 乐观锁保证，残量无任何后续业务消费）。
-- 写侧已修（PigCutRecordServiceImpl / ProductProductionServiceImpl 领用扣重后同事务
-- drain 清零 + 写 flow_type=loss 流水）；本迁移只清历史存量。
--
-- 谓词（两步共用）：白条篮（white_bar_no 非空）+ 余量>0 + 未软删 + 该白条已无未领产出行
-- （NOT EXISTS pickup_status=0/NULL 的 inhouse 行 → 篮子不可能再被领用，余量即死残量）。
-- 预冷损耗账 t_warehouse_loss_flow 领用时已记（writePickupPrecoolLoss / whitebar_out），
-- 此处只写 stock_flow 留痕，绝不写 loss_flow（再写 = 损耗总览双算）。
-- flow_no 用 FCLEAN+4 位序号（避开 BizCode F+日期 格式，不占号段；uk_flow_no 天然不撞）。
-- ⚠️ INSERT 必须先于 UPDATE（谓词消费 product_stock，清零后 INSERT 会 0 命中）。

-- 步骤 1：按残量行补「损耗出库」流水（OT/loss，change_num 负 / change_quantity 正）
INSERT INTO t_warehouse_stock_flow
    (tenant_id, flow_no, flow_date, product_id, warehouse_id, inout_type, flow_type,
     change_num, change_quantity, ear_no, white_bar_no,
     create_time, update_time, del_flag, del_unique, remark)
SELECT t.tenant_id,
       CONCAT('FCLEAN', LPAD(t.rn, 4, '0')),
       NOW(),
       COALESCE(t.product_id, 0),
       t.location_id,
       'OT',
       'loss',
       -t.product_stock,
       t.product_stock,
       t.ear_no,
       t.white_bar_no,
       NOW(), NOW(), '0', 0,
       '历史白条领用残量转损耗出库（入库重-领用重，FIX-ADMIN-0722 一次性清理）'
FROM (
    SELECT ls.id, ls.tenant_id, ls.product_id, ls.location_id, ls.ear_no,
           ls.white_bar_no, ls.product_stock,
           ROW_NUMBER() OVER (ORDER BY ls.id) AS rn
    FROM t_warehouse_location_stock ls
    WHERE ls.white_bar_no IS NOT NULL
      AND ls.product_stock > 0
      AND ls.del_flag = '0'
      AND NOT EXISTS (
          SELECT 1 FROM t_warehouse_product_inhouse ih
          WHERE ih.white_bar_no = ls.white_bar_no
            AND ih.del_flag = '0'
            AND (ih.pickup_status = 0 OR ih.pickup_status IS NULL)
      )
) t;

-- 步骤 2：同谓词篮子余量清零（不软删，保留 0 行可追溯盘点）
UPDATE t_warehouse_location_stock ls
   SET ls.product_stock = 0,
       ls.update_time = NOW()
 WHERE ls.white_bar_no IS NOT NULL
   AND ls.product_stock > 0
   AND ls.del_flag = '0'
   AND NOT EXISTS (
       SELECT 1 FROM t_warehouse_product_inhouse ih
       WHERE ih.white_bar_no = ls.white_bar_no
         AND ih.del_flag = '0'
         AND (ih.pickup_status = 0 OR ih.pickup_status IS NULL)
   );
