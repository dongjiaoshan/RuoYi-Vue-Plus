-- ============================================================
-- CROSS-FLOW-001（D10）：BAR_NO 业务码规则 seed
--
-- 出栏 → 燎毛 listener 自动创建白条（t_warehouse_bar_info）时需生成 bar_id 业务码。
-- 与 D9 closing Group B 已治理的 BURN_NO / CUT_NO 范式一致，统一走 BizCodeService。
--
-- 格式：BAR{yyMMdd}{seq4}（每日重置，例 BAR2606040001）
-- ============================================================

INSERT IGNORE INTO t_md_biz_code_rule
  (code_type, pattern,             daily_reset, prefix, seq_length, status, create_by, create_time)
VALUES
  ('BAR_NO', 'BAR{yyMMdd}{seq4}',  1,           'BAR',  4,          '0',    1,         NOW());
