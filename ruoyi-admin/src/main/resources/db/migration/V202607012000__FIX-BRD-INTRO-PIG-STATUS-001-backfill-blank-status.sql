-- FIX-BRD-INTRO-PIG-STATUS-001 回填空 current_status 的历史种猪（内部引种报错根因）
-- 早期 seed 种猪 current_status 为空（''/NULL），内部引种提交时 parseLifecycle 校验落空抛
-- 「猪只状态值 [null] 不在字典 djs_pig_lifecycle 范围内」。任何依赖 lifecycle 的事件（引种/配种/转移…）
-- 在这些猪上都会失败。按 createPig 初始化口径回填：公猪 → BOAR_ACTIVE，其余（母猪/育肥/仔猪）→ HB。
-- status_started_at 同步回填（空时取建档时间兜底），供 lifecycle 时长计算。
UPDATE t_farm_pig_info
SET current_status = CASE WHEN pig_type = 'boar' THEN 'BOAR_ACTIVE' ELSE 'HB' END,
    status_started_at = COALESCE(status_started_at, create_time, NOW())
WHERE del_flag = '0'
  AND (current_status IS NULL OR current_status = '');
