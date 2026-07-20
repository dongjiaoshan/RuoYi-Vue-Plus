-- FIX-FEED-001 #50：采食领用出库去向补「部门领用」字典值
-- 决策：_open-issues #50 = a（前端固定传值，不动后端 MatPickBo @NotNull 约束）
-- 背景：
--   - 甲方口径：采食领用即部门内部消耗，工人不选去向 → 领用子页删「出库去向」radio，
--     提交时固定传 stockOutDest='dept'（部门领用）。
--   - djs_stock_out_dest 现有 5 值（kitchen/mine/daye_store/personal/feed=投喂），无「部门领用」
--     → 本 SQL 补第 6 值 dept=部门领用，让前端固定传值能落库 + admin 流水列表能翻译显示。
-- 落地：
--   1. 本 SQL 在 djs_stock_out_dest 追加 1 条值（dept / 部门领用，dict_code=1006305）
--   2. 跑后需 flush redis 字典缓存（_post-init.sh）—— 否则 admin 流水去向列读不到新值

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, remark, create_time)
VALUES
  (1006305, '1001', 5, '部门领用', 'dept', 'djs_stock_out_dest', '', 'info', 'N', '采食领用出库去向：部门内部消耗（工人不选，前端固定传值）', NOW());
