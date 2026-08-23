-- V6 row115：后台出库（矿山/厨房/劲牌等直接来仓库拿走）产出行没有任何标记，store_id 为空，
-- 被发货月台的「同门店 OR 未绑门店」当通用件捞进门店可发清单 —— 门店卡「生产量」凭空多一件、
-- 需求满足率虚高，还能被出车发货发给门店（staging 实测：ST0005 半扇 需求 1 头却显示生产量 2 头/100kg）。
-- 出库口现已写 deliver_dest='warehouse_out'（同 'gift' 那条口径：非发货月台去向不进可发清单）；
-- 本脚本补齐存量行。识别靠出库口自己生成的备注前缀「后台出库 去向=」，它是这批行的唯一既有标记。
UPDATE t_warehouse_product_production
   SET deliver_dest = 'warehouse_out'
 WHERE del_flag = '0'
   AND deliver_dest IS NULL
   AND remark LIKE '后台出库 去向=%';
