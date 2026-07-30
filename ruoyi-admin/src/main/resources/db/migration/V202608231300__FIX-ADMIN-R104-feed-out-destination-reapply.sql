-- admin row104：饲料饲喂出库需显示出库去向「猪只饲料」。
-- 字典改名 + 历史空值回填；新写入由 MatFlowServiceImpl 固定 stock_out_dest=feed。
UPDATE sys_dict_data
   SET dict_label = '猪只饲料',
       update_time = NOW(),
       remark = '饲料喂养出库去向'
 WHERE dict_type = 'djs_stock_out_dest'
   AND dict_value = 'feed';

UPDATE t_warehouse_stock_flow
   SET stock_out_dest = 'feed',
       update_time = NOW()
 WHERE flow_type = 'feed_out'
   AND (stock_out_dest IS NULL OR stock_out_dest = '')
   AND del_flag = '0';
