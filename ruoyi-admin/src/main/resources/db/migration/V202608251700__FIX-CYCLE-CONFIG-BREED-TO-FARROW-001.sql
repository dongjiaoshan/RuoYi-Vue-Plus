-- ============================================================================
-- FIX-CYCLE-CONFIG-BREED-TO-FARROW-001  重申 R53 的生产周期配置口径（被重灌冲掉了）
--
-- 邓博 R53 已经定过：预产期 = 配种日 + sow_breed_to_farrow_days，该值应为 **114**（妊娠期），
-- 由 V202607301500__FIX-DENGBO-R53-gestation-114.sql 把 141 改成 114，并清掉 custom_value=0。
--
-- 为什么又要改一遍：初始数据重灌把 t_farm_production_cycle_config 清空过，
-- 补回脚本（doc/_audit/data-reseed/_gen/_restore_flyway_config.py）**只重放迁移里的 INSERT**，
-- 于是最早 V202606260050 里的 default_value=141 被原样灌回、R53 那条 UPDATE 修复被冲掉。
-- 本迁移把口径重新钉住，并做成「值无关」的写法（不带 WHERE default_value=141），重灌多少次都能纠正。
-- 补回脚本本身也已改成按版本序重放 INSERT+UPDATE，不再丢后续修复。
--
-- custom_value=0 一并清掉：0 天无业务含义 = 未定制（R53 原话）。它会让 mp 分娩/断奶页的
-- 日龄门整片失效（picker 把所有猪都算成到期）。⚠️ 入口没堵——admin「母猪生产配置」表单保存时
-- ProductionCycleConfigServiceImpl.batchSaveValues 对已存在行无条件 setCustomValue，
-- 未填字段会再写回 0。所以这条清理是治标，入口的过滤另提。
--
-- 幂等：按 config_key 定点 UPDATE，重跑无副作用。
-- ============================================================================
SET NAMES utf8mb4;

UPDATE t_farm_production_cycle_config
   SET default_value = 114, update_time = NOW()
 WHERE config_key = 'sow_breed_to_farrow_days' AND tenant_id = '1001';

UPDATE t_farm_production_cycle_config
   SET default_value = 25, update_time = NOW()
 WHERE config_key = 'sow_farrow_to_wean_days' AND tenant_id = '1001';

-- 天数 0 = 未定制（R53）。只清 0，人真填的非 0 定制值不动。
UPDATE t_farm_production_cycle_config
   SET custom_value = NULL, update_time = NOW()
 WHERE tenant_id = '1001' AND custom_value = 0;
