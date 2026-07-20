-- ============================================================
-- FIX-WMS-TRANSFER-DEST（row163）：猪肉转移出库去向补 djs_stock_out_dest 「冻品库」值
--
--   frozen_store 冻品库（PigTransfer 转移出库流水的 stock_out_dest，出库记录页去向列渲染用）
--
-- 说明：
--   - WS13 猪肉转移出库流水（transfer_out）只写了 remark，未写 stock_out_dest → 出库记录页去向列空
--   - djs_stock_out_dest 现有 11 值（kitchen/mine/daye_store/personal/feed/dept/ship_dock/
--     dept_pick/bar_cut/prod_pick/check_loss），无「冻品库」→ 补此值
--   - dict_code 1006300-1006310 连续段已满、1006311 被 djs_abnormal 占用 → 借用 1006411（未占）
--   - dict_sort 现有已用到 10，新增续 11
--   - sys_dict_data 是 ruoyi 系统表，按 ruoyi 风格写（tenant_id='1001'，无 create_dept 列）
--   - INSERT IGNORE + dict_code PK 定位，重复执行幂等
--   - 改后须 flush *:sys_dict redis 缓存（script/sql/djs/_post-init.sh）
-- ============================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1006411, '1001', 11, '冻品库', 'frozen_store', 'djs_stock_out_dest', '', 'info', 'N', 1, NOW(), 'FIX-WMS-TRANSFER-DEST 猪肉鲜品库→冻品库转移出库去向（row163）');
