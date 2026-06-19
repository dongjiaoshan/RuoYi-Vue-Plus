-- 燎毛产出的白条（整只/半只/猪头/猪蹄）是下游分割/打包的原材料，product_attr 应为 2=原材料。
-- 标准白条 seed 误标为 1=生产产品（=对外打包后成品），导致 mp 燎毛产品入库按「只取原材料」规则取不到。
-- 修正标准白条主数据为原材料；不动 admin 用户自建白条（由用户在产品配置里自行设置产品属性）。
UPDATE t_warehouse_product_info
   SET product_attr = 2
 WHERE belong_type = 'white_bar'
   AND product_id LIKE 'PROD-WHITE-BAR-%'
   AND product_attr <> 2;
