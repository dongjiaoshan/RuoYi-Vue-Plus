-- F0FIX-F0-2（STOCK-D2-05）：cut_out 出库流水 warehouse_id NULL 回填
-- 写侧根因：writeBarCutOutFlow / writeWhiteBarOutFlow 直取 ProductInhouse.location_id（可空）写
-- warehouse_id，NULL 行在库存总览明细（按 产品×库位 分组）生成「库位空、期末为负」幽灵行；
-- doc/11 §2.3 stock_flow.warehouse_id 必填。写侧已补回落（inhouse.location_id 空 → 白条库存行 location_id）。
--
-- 回填依据：cut_out 流水 = 白条离白条库，来源库位即该白条产出行入库时所在库位。按写入方取值链路回查：
--   Pass 1（半只键，最精确）：同 white_bar_no 的 t_warehouse_product_inhouse.location_id
--     （该流水正是从这条 inhouse 行写出，其 location_id 后补/同批行有值时即真实来源库位）；
--   Pass 2（整猪键兜底）：white_bar_no 空（mp 整只领用路径）→ 同 ear_no 的 inhouse.location_id
--     （同一头猪的燎毛产出行落同一白条库，取 MIN 保证确定性）。
-- 两个来源均无值的行保持 NULL（无法推断，不造数据）；读侧幽灵行随 NULL 行减少自然消退。
-- 幂等：只更新 warehouse_id IS NULL 的行，回填过的行不再命中。

UPDATE t_warehouse_stock_flow f
  JOIN (
        SELECT white_bar_no, MIN(location_id) AS loc
          FROM t_warehouse_product_inhouse
         WHERE white_bar_no IS NOT NULL
           AND location_id IS NOT NULL
           AND del_flag = '0'
         GROUP BY white_bar_no
       ) src ON src.white_bar_no = f.white_bar_no
   SET f.warehouse_id = src.loc
 WHERE f.warehouse_id IS NULL
   AND f.flow_type = 'cut_out';

UPDATE t_warehouse_stock_flow f
  JOIN (
        SELECT ear_no, MIN(location_id) AS loc
          FROM t_warehouse_product_inhouse
         WHERE ear_no IS NOT NULL
           AND location_id IS NOT NULL
           AND del_flag = '0'
         GROUP BY ear_no
       ) src ON src.ear_no = f.ear_no
   SET f.warehouse_id = src.loc
 WHERE f.warehouse_id IS NULL
   AND f.flow_type = 'cut_out';
