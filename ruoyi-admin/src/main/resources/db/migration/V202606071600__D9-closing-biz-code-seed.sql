-- ============================================================
-- D9 closing Group B：BizCodeService 集中治理（_open-issues #14 + #15）
--
-- 新增 3 类业务编码规则（PACK_NO 已 seed 于 V202605210900，不再重复）：
--   BURN_NO  每日重置  BURN{yyMMdd}{seq4}   D8 PigBurnRecordServiceImpl inline 迁入
--   CUT_NO   每日重置  CUT{yyMMdd}{seq4}    D9 PigCutRecordServiceImpl inline 迁入
--   PLAN_NO  按年重置  PLAN-{yyyy}-{seq3}   D9 PlantPlanServiceImpl inline 迁入（daily_reset=2 按年重置）
--
-- 兼容性：BURN_NO / CUT_NO 维持已落地数据的 BURN+yyMMdd+seq4 / CUT+yyMMdd+seq4 格式
--        （pattern 中 {yyMMdd} 占位符由 D9 closing 同步在 BizCodeGeneratorImpl.format 新增）。
-- PLAN_NO pattern 中 {yyyy} + {seq3} 占位符亦由 D9 closing 在 format 中新增。
-- ============================================================

INSERT IGNORE INTO t_md_biz_code_rule
  (code_type,  pattern,                 daily_reset, prefix, seq_length, status, create_by, create_time)
VALUES
  ('BURN_NO',  'BURN{yyMMdd}{seq4}',    1,           'BURN', 4,          '0',    1,         NOW()),
  ('CUT_NO',   'CUT{yyMMdd}{seq4}',     1,           'CUT',  4,          '0',    1,         NOW()),
  ('PLAN_NO',  'PLAN-{yyyy}-{seq3}',    2,           'PLAN', 3,          '0',    1,         NOW());
