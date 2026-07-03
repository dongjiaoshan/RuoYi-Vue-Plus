-- ============================================================
-- WS13 猪肉转移（row143）：库存查询页猪肉鲜品库 → 冻品库转移，新增 2 个 djs_flow_type 字典值
--
--   transfer_out 转移出库（猪肉鲜品库减库存流水）
--   transfer_in  转移入库（冻品库加库存流水）
--
-- 说明：
--   - djs_flow_type 现有 dict_code 已用到 102450026（feed_out），新增续 102450027+
--   - dict_sort 现有已用到 26，新增续 27/28
--   - INSERT IGNORE + dict_type+dict_value 精确定位，重复执行幂等
--   - 改后须 flush *:sys_dict redis 缓存（script/sql/djs/_post-init.sh）
-- ============================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (102450027, '1001', 27, '转移出库', 'transfer_out', 'djs_flow_type', '', 'warning', 'N', 1, NOW(), 'WS13 猪肉鲜品库转移出库（PigTransfer 源侧）'),
    (102450028, '1001', 28, '转移入库', 'transfer_in',  'djs_flow_type', '', 'success', 'N', 1, NOW(), 'WS13 冻品库转移入库（PigTransfer 目标侧）');
