-- 作物→产品只认作物产品配置 t_plant_crop_product（首行 = 主产品）。
-- t_plant_crop_info 上的旧单列 related_product / pick_unit_price 已无代码读写，删除。
-- 删列前两库实测：在用作物的 related_product / pick_unit_price 分别恒等于产品配置首行的 product_id / perf_price。
SET NAMES utf8mb4;

-- 绩效结算对「该产品没填绩效金额」回落作物单价；把那个单价写进配置行，结算结果不变。
UPDATE t_plant_crop_product cp
JOIN t_plant_crop_info c ON c.id = cp.crop_id
SET cp.perf_price = c.pick_unit_price
WHERE cp.perf_price IS NULL
  AND c.pick_unit_price IS NOT NULL
  AND cp.del_flag = '0';

ALTER TABLE t_plant_crop_info
  DROP COLUMN related_product,
  DROP COLUMN pick_unit_price;
