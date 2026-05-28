-- D9 testing-human §5.1 实测 hotfix: seed djs_stock_out_dest 字典
-- 触发：Kevin 2026-05-28 17:52 测包材领用「出库去向」radio-group 全空，无法提交（canSubmitPick 必填）
-- 根因：
--   - doc/11 §0.3 已定义 djs_stock_out_dest 权威 4 值（kitchen/mine/daye_store/personal）
--   - D9 prompt WMS-MAT-001 假设"D7 WMS-MD-001 已 seed"，实际 D7 漏 seed；D9 WMS-MAT-001 实施时也未补
--   - 同时 DictTypeConstants.java 仓库域白名单未含此 dict_type → 即使 seed 也被 /djs/dict/full 拦截
-- 处置：
--   1. 本 SQL seed djs_stock_out_dest 4 条值
--   2. DictTypeConstants.java D 仓库域 4 类 → 5 类，加 STOCK_OUT_DEST 常量
--   3. 跑完必须刷 Redis 字典缓存：bash code/main/RuoYi-Vue-Plus/script/sql/djs/_post-init.sh

-- ------------------------------------------------------------
-- djs_stock_out_dest 出库去向（doc/11 §0.3 R33 权威 4 值）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100630, '1001', '出库去向', 'djs_stock_out_dest', NULL, NOW(), '仓库：出库流水的 stock_out_dest，WMS-MAT-001 领用 / WMS-PIG-002 分割等用');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006300, '1001', 0, '厨房',     'kitchen',     'djs_stock_out_dest', '', 'primary', 'Y', NULL, NOW()),
  (1006301, '1001', 1, '矿山',     'mine',        'djs_stock_out_dest', '', 'success', 'N', NULL, NOW()),
  (1006302, '1001', 2, '大冶门店', 'daye_store',  'djs_stock_out_dest', '', 'warning', 'N', NULL, NOW()),
  (1006303, '1001', 3, '个人',     'personal',    'djs_stock_out_dest', '', 'info',    'N', NULL, NOW());
