-- BRD-EVENT-002-be: Pig 实体补 mating_count + last_mating_date 两字段（D5 audit a-1）
--
-- mating_count: 母猪累计配种次数（BREED 事件 +1，用于异常母猪识别"配种 ≥ 3 次仍未孕"）
-- last_mating_date: 最近一次配种日期（用于计算妊娠天数 vs production_cycle_config.pregnancy_days）
--
-- 写入路径：BreedingServiceImpl.recordBreeding → pigCoreService.fireEvent(BREED) →
--          PigCoreServiceImpl.applyEventSideEffects → wrapper-only update（防 race condition）

ALTER TABLE t_farm_pig_info
    ADD COLUMN mating_count INT NOT NULL DEFAULT 0 COMMENT '累计配种次数（每次 BREED 事件 +1）' AFTER parity,
    ADD COLUMN last_mating_date DATE NULL COMMENT '最近一次配种日期' AFTER mating_count;
