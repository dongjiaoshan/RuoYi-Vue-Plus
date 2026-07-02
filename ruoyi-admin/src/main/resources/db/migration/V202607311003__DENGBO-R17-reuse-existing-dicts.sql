-- row17 修正（Kevin 2026-07-02）：出库去向字典系统已有，复用不新建。
-- 1) 删除本轮多建的冗余 djs_bar_out_dest（去向改复用 djs_stock_out_dest = 矿山/厨房/大冶门店/个人…，前端已切）。
-- 2) 出库方式复用既有 djs_bar_out_method（发货领用=1 / 分割间=2），补「后台出库=3」（仓库出库终态 out_method）。
DELETE FROM sys_dict_data WHERE dict_type = 'djs_bar_out_dest';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_bar_out_dest';

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
  (1022202, '1001', 2, '后台出库', '3', 'djs_bar_out_method', NULL, 'default', 'N', 1, NOW(), 'row17 仓库出库=矿山/厨房直接取走，终态 out_method=3');
