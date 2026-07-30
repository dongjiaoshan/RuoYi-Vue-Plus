-- ============================================================================
-- FIX-ADMIN-R149  门店需求状态标签配色去重（5 态 5 色，互不重复）
--
-- 「需求下单」列表的需求状态列走 djs_store_demand_status 字典的 list_class 渲染 el-tag，
-- 相邻流转态用同一个 type 就退化成同一块颜色，操作员分不出单子走到哪一步。
-- Element Plus el-tag 只有 primary / success / info / warning / danger 五种 type，
-- 正好一态一色，按流程推进方向排：
--   待确认 SUBMITTED  warning 橙  —— 待门店/仓库动作
--   已确认 CONFIRMED  info    灰  —— 已受理，等待发货
--   已发货 SHIPPED    primary 蓝  —— 在途
--   确认到店 ARRIVED  success 绿  —— 终态完成
--   已删除 DELETED    danger  红  —— 作废
--
-- 跑完刷 Redis 字典缓存：本地 bash script/sql/djs/_post-init.sh
--                       staging bash ops/redis-flush-dict.sh staging --yes
-- ============================================================================
SET NAMES utf8mb4;

UPDATE sys_dict_data SET list_class = 'warning', update_time = NOW()
 WHERE dict_type = 'djs_store_demand_status' AND tenant_id = '1001' AND dict_value = 'SUBMITTED';

UPDATE sys_dict_data SET list_class = 'info', update_time = NOW()
 WHERE dict_type = 'djs_store_demand_status' AND tenant_id = '1001' AND dict_value = 'CONFIRMED';

UPDATE sys_dict_data SET list_class = 'primary', update_time = NOW()
 WHERE dict_type = 'djs_store_demand_status' AND tenant_id = '1001' AND dict_value = 'SHIPPED';

UPDATE sys_dict_data SET list_class = 'success', update_time = NOW()
 WHERE dict_type = 'djs_store_demand_status' AND tenant_id = '1001' AND dict_value = 'ARRIVED';

UPDATE sys_dict_data SET list_class = 'danger', update_time = NOW()
 WHERE dict_type = 'djs_store_demand_status' AND tenant_id = '1001' AND dict_value = 'DELETED';
