-- admin row178：门店退回确认必失败的产品数据补齐。
--
-- 背景：确认入库时先把成品折成原材料（product_material），再按该产品的预设库位 store_location_id
-- 解析入库库位；解析不出来就抛 400（小程序上只弹一条红 toast，门店以为退成功、仓库永远收不到）。
--
-- 猪肉「黑毛猪猪尾巴」(P0133) 自身没配预设库位。它当前靠 product_material → 原材料「猪尾巴」的
-- 库位兜底，所以确认还走得通；这里把预设库位直接补到成品上，去掉对那一层兜底的依赖。
-- 库位不写死：从它自己的原材料上取（同 belong_type 的猪肉原材料统一用「猪肉鲜品库」）。
-- 幂等：补完 store_location_id 非空，重复执行匹配 0 行。
UPDATE t_warehouse_product_info p
  JOIN t_warehouse_product_info m ON m.id = p.product_material AND m.del_flag = '0'
   SET p.store_location_id = m.store_location_id
 WHERE p.product_id = 'P0133'
   AND p.del_flag = '0'
   AND (p.store_location_id IS NULL OR p.store_location_id = '')
   AND m.store_location_id IS NOT NULL
   AND m.store_location_id <> '';

-- 礼盒（belong_type='gift_box'，P0083/P0084/P0085/P0086）不在这里补数据：
-- 礼盒是多种原料的组合装，product_material 单值表达不了拆回哪些原材料、各多少，
-- 配一个假原材料只会把错账写进库存。改为在门店退回选品与提交两层拦掉（见
-- StoreReturnServiceImpl.assertReturnableToWarehouse / dropGiftBoxCandidates），工人选不到、提交被拒。
