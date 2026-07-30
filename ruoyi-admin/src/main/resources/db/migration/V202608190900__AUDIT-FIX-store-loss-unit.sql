-- AUDIT-FIX store-5：白条分割损耗汇总行（product_id=0 哨兵）补产品单位。
-- 该行本质是 kg 重量（到店重 − 退回入库重），单位为空时前端按「非 kg」取整丢 3 位小数。
UPDATE t_store_loss_record
SET product_unit = 'kg'
WHERE loss_type = 'white_bar_split_loss'
  AND product_unit IS NULL;
