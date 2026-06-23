-- 外购果蔬月台入库「入库方式」文案统一为「外购产品月台入库」（仓库-小程序 row33）。
-- 按 dict_type + dict_value 定位（不用 dict_code 102450017：该 dict_code 被 receive_in 的
-- INSERT IGNORE 共用，按 dict_code 改会误伤）。改后须 flush *:sys_dict redis 缓存（_post-init.sh）。
UPDATE sys_dict_data
   SET dict_label = '外购产品月台入库'
 WHERE dict_type = 'djs_flow_type'
   AND dict_value = 'veg_purchase_in';
