-- 损耗 / 饲喂类流水按「动不动库存篮」区分 flow_type（StockFlowTypes）：
--   loss        损耗（扣库存篮）：白条残量
--   settle_loss 剩余结转损耗（扣库存篮）：地块收口 / 果蔬出库完成
--   pick_loss   领用后损耗（不扣库存篮，领用出库时已扣过）
--   pick_feed   领用后饲喂（不扣库存篮）
-- 库存日 / 月汇总按库位回放流水，pick_loss / pick_feed 不参与回放；「今日领用剩余」认 loss + pick_loss、feed_out + pick_feed。
-- 生效：djs_flow_type 已在 redis 缓存，部署后必须 flush 字典缓存。
SET NAMES utf8mb4;

INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1026102801, '1001', 60, '结转损耗', 'settle_loss', 'djs_flow_type', '', 'danger', 'N', 1, NOW(), '地块收口 / 果蔬出库完成的剩余结转损耗'),
    (1026102802, '1001', 61, '领用损耗', 'pick_loss', 'djs_flow_type', '', 'danger', 'N', 1, NOW(), '物资领用后登记的损耗，不扣库存篮'),
    (1026102803, '1001', 62, '领用饲喂', 'pick_feed', 'djs_flow_type', '', 'warning', 'N', 1, NOW(), '物资领用后登记的饲喂，不扣库存篮')
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type),
    list_class = VALUES(list_class), remark = VALUES(remark);

-- 历史领用后损耗：物资领用（mat）的可打包食品 / 猪肉损耗与药品（med）损耗都只剥领用剩余、没扣库存篮。
UPDATE t_warehouse_stock_flow f
JOIN t_warehouse_loss_flow lf ON lf.source_flow_id = f.id AND lf.del_flag = '0'
LEFT JOIN t_warehouse_product_info p ON p.id = f.product_id
SET f.flow_type = 'pick_loss'
WHERE f.flow_type = 'loss'
  AND (lf.source_biz_type = 'med'
       OR (lf.source_biz_type = 'mat' AND p.belong_type IN ('vegetable', 'egg', 'dry_good', 'other', 'pork')));

-- 历史领用后饲喂：可打包食品的饲喂只剥领用剩余、没扣库存篮。
UPDATE t_warehouse_stock_flow f
JOIN t_warehouse_product_info p ON p.id = f.product_id
SET f.flow_type = 'pick_feed'
WHERE f.flow_type = 'feed_out'
  AND p.belong_type IN ('vegetable', 'egg', 'dry_good', 'other');
