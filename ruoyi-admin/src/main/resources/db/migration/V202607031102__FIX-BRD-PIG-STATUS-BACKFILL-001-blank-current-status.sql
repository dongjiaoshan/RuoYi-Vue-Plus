-- FIX-BRD-PIG-STATUS-BACKFILL-001 — 回填 current_status 空串/NULL 脏数据（邓博 2026-06-17 #39 阉割阻塞）。
--
-- 根因：staging 有 96 仔猪 / 20 育肥 / 2 公猪 current_status='' （空串，非 NULL）。这些猪选中后
--   任何事件走 PigCoreServiceImpl.fireEvent → parseLifecycle('') → 抛 pig.state.invalid
--   「猪只状态值 [null] 不在字典 djs_pig_lifecycle 范围内」（阉割录入即因此报错）。
--   且 picker 过滤只 .isNotNull(current_status) 排了 NULL、漏排空串 → 这些脏数据猪仍进 picker 候选。
--   正常创建路径（createPig / batchEarTag）都已正确置初始态，空串系一次性 bulk 测试数据遗留。
--
-- 回填口径与 createPig 初始态一致（PigCoreServiceImpl.createPig）：
--   公猪(boar) → BOAR_ACTIVE（公猪唯一活跃态）；仔猪/育肥(piglet/fattening) → HB（后备初始态）。
--   sow 无空串（全 DN/FM/FQ/HB/END），不动。幂等：只命中 NULL/'' 行，重跑安全。
-- 纯数据 SQL，Flyway 自动应用，无需 mvn install。V202607031102 > 当前 max V202607031101。

UPDATE t_farm_pig_info
SET current_status = 'BOAR_ACTIVE'
WHERE del_flag = '0'
  AND pig_type = 'boar'
  AND (current_status IS NULL OR current_status = '');

UPDATE t_farm_pig_info
SET current_status = 'HB'
WHERE del_flag = '0'
  AND pig_type IN ('piglet', 'fattening')
  AND (current_status IS NULL OR current_status = '');
