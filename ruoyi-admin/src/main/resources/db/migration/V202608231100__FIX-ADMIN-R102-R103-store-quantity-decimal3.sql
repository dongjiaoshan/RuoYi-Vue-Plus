-- admin rows 102-103：门店退回与日盘点数量统一支持 0.001 精度。
-- 新数据通过 DECIMAL(12,3) 保留第三位；历史只使用已有三位 goods_weight/received_weight
-- 且产品单位明确为 KG/公斤的记录做安全回填，其余已丢失精度的数据不猜测。

ALTER TABLE t_store_return
    MODIFY COLUMN return_quantity DECIMAL(12,3) NOT NULL COMMENT '退回数量',
    MODIFY COLUMN received_qty    DECIMAL(12,3) NULL COMMENT '仓库实收量（仓库确认时填）';

ALTER TABLE t_store_daily_ledger
    MODIFY COLUMN opening_qty   DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '期初库存（V1 手填，首次建账即期初）',
    MODIFY COLUMN inbound_qty   DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '当日入库量（可用仓库发出量预填，口径=发出非实收）',
    MODIFY COLUMN sale_qty      DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '销售量（可由 t_store_sale_record 当日聚合预填）',
    MODIFY COLUMN gift_qty      DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '赠送量（手填）',
    MODIFY COLUMN return_qty    DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '退货量（顾客退货，可由 t_store_return 当日聚合预填）',
    MODIFY COLUMN wh_return_qty DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '退回量（门店退回仓库，可由 t_store_return store_to_warehouse 当日聚合预填）',
    MODIFY COLUMN loss_qty      DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '损耗量（手填，未填按 0）',
    MODIFY COLUMN closing_qty   DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '期末库存（service 算：期初+入库−销售−赠送−退货−损耗）';

-- batchCreate 对 KG 产品将同一退回值同时写入 return_quantity 与 goods_weight；
-- goods_weight 已在 V202608091500 扩为三位，因此可作为旧 KG 退回量的可靠恢复源。
UPDATE t_store_return r
JOIN t_warehouse_product_info p
  ON p.id = r.product_id
 AND p.tenant_id = r.tenant_id
SET r.return_quantity = r.goods_weight
WHERE r.goods_weight IS NOT NULL
  AND (LOWER(TRIM(p.product_unit)) = 'kg' OR TRIM(p.product_unit) = '公斤');

-- 仓库确认 KG 产品时 received_weight 缺省即 received_qty，可安全恢复实收量第三位。
UPDATE t_store_return r
JOIN t_warehouse_product_info p
  ON p.id = r.product_id
 AND p.tenant_id = r.tenant_id
SET r.received_qty = r.received_weight
WHERE r.received_weight IS NOT NULL
  AND (LOWER(TRIM(p.product_unit)) = 'kg' OR TRIM(p.product_unit) = '公斤');

-- 已保存的门店日台账只回填存在可靠 goods_weight 来源的 KG 退回聚合列；
-- opening/sale/gift/loss/closing 等历史第三位没有独立可靠来源，不做推测性回填。
UPDATE t_store_daily_ledger l
JOIN (
    SELECT r.tenant_id,
           r.store_id,
           r.product_id,
           DATE(r.return_date) AS ledger_date,
           SUM(r.goods_weight) AS quantity
      FROM t_store_return r
      JOIN t_warehouse_product_info p
        ON p.id = r.product_id
       AND p.tenant_id = r.tenant_id
     WHERE r.del_flag = '0'
       AND r.return_direction = 'customer_to_store'
       AND r.goods_weight IS NOT NULL
       AND (LOWER(TRIM(p.product_unit)) = 'kg' OR TRIM(p.product_unit) = '公斤')
     GROUP BY r.tenant_id, r.store_id, r.product_id, DATE(r.return_date)
) reliable_return
  ON reliable_return.tenant_id = l.tenant_id
 AND reliable_return.store_id = l.store_id
 AND reliable_return.product_id = l.product_id
 AND reliable_return.ledger_date = l.ledger_date
SET l.return_qty = reliable_return.quantity
WHERE l.del_flag = '0';

UPDATE t_store_daily_ledger l
JOIN (
    SELECT r.tenant_id,
           r.store_id,
           r.product_id,
           DATE(r.return_date) AS ledger_date,
           SUM(r.goods_weight) AS quantity
      FROM t_store_return r
      JOIN t_warehouse_product_info p
        ON p.id = r.product_id
       AND p.tenant_id = r.tenant_id
     WHERE r.del_flag = '0'
       AND r.return_direction = 'store_to_warehouse'
       AND r.goods_weight IS NOT NULL
       AND (LOWER(TRIM(p.product_unit)) = 'kg' OR TRIM(p.product_unit) = '公斤')
     GROUP BY r.tenant_id, r.store_id, r.product_id, DATE(r.return_date)
) reliable_wh_return
  ON reliable_wh_return.tenant_id = l.tenant_id
 AND reliable_wh_return.store_id = l.store_id
 AND reliable_wh_return.product_id = l.product_id
 AND reliable_wh_return.ledger_date = l.ledger_date
SET l.wh_return_qty = reliable_wh_return.quantity
WHERE l.del_flag = '0';
