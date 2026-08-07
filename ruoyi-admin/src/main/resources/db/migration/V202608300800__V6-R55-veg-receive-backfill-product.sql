-- V6 row55：果蔬月台自产收货由「按作物聚合」改为「按产品聚合」，回填历史收货记录的 product_id。
--
-- 为什么必须回填：改动后待入库量 = 该(作物,产品)月台量 − 该(作物,产品)已收量。历史 self 收货行
-- product_id 全空，若不回填，这些行会挂到「product_id 回落值」那一档上；一旦某作物的
-- related_product 与实际收的产品不是同一个，已收量就扣不到正确的产品头上，待入库量凭空变大。
--
-- 回填规则（两级，越精确越优先）：
--   ① 同 (crop_id, plot_id) 的月台明细行 t_warehouse_handle_record(record_type=2, handle_target=2)
--      只有唯一一个 product_id → 就是它。多产品之前的数据天然满足这一条。
--   ② 取不到唯一值（该地块当时送了多个产品，无法判定这笔收的是哪个）→ 保持 NULL，
--      交给查询侧的 COALESCE(product_id, crop.related_product) 回落，行为与改动前一致。
--
-- 只动 receive_type=1（自产）；外购行 product_id 本来就有值，不碰。

UPDATE t_warehouse_veg_receive vr
   SET vr.product_id = (
        SELECT MIN(hr.product_id)
          FROM t_warehouse_handle_record hr
         WHERE hr.del_flag = '0'
           AND hr.tenant_id = vr.tenant_id
           AND hr.record_type = 2
           AND hr.handle_target = 2
           AND hr.crop_id = vr.crop_id
           AND hr.plot_id = vr.plot_id
           AND hr.product_id IS NOT NULL
        HAVING COUNT(DISTINCT hr.product_id) = 1
       )
 WHERE vr.del_flag = '0'
   AND vr.receive_type = 1
   AND vr.product_id IS NULL;
