-- BRD-FIX-DICT-NAMING-001：养殖域 dict_type 命名收敛（真 rename，对齐 doc/11 权威名）
-- Kevin 2026-06-03 拍板路径 b：全量 rename DB dict_type 对齐 doc/11，而非映射表。
--
-- 处置：
--   1. 删孤儿 djs_elimination_reason（D2 INIT seed，被 D9 djs_eliminate_reason 取代，4 行重复值）
--   2. rename 漂移 5 处到 doc/11 权威名：
--        djs_pig_gender       → djs_pig_sex       （doc/11 R8；dict_value 已是 M/F，无需迁值）
--        djs_breeding_type    → djs_mating_method （doc/11；dict_value 1/2/AI/LQ/RJ 保留）
--        djs_out_house_dest   → djs_market_dest   （doc/11；dict_value slaughter/sale_out/other 保留）
--        djs_eliminate_reason → djs_culling_reason（doc/11 R11）
--        djs_eliminate_dest   → djs_culling_dest  （doc/11 R12）
--   3. rename 语义碰撞分离（领用动作 vs 用药类型）：
--        djs_medicine_usage_type（use/return/loss 领用动作）→ djs_med_pick_action
--        djs_medicine_use_type（health/treatment/vaccine 用药类型）保留不动
--
-- 注：rename 同步 sys_dict_type.dict_type + sys_dict_data.dict_type 两表。
--     entity 注释 / DictTypeConstants 常量 / admin useDict / mp DictPicker 已在代码侧同步改。

-- ============ 1. 删孤儿 djs_elimination_reason ============
DELETE FROM sys_dict_data WHERE dict_type = 'djs_elimination_reason';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_elimination_reason';

-- ============ 2. rename 漂移 5 处 ============
-- 2.1 djs_pig_gender → djs_pig_sex
UPDATE sys_dict_type SET dict_type = 'djs_pig_sex' WHERE dict_type = 'djs_pig_gender';
UPDATE sys_dict_data SET dict_type = 'djs_pig_sex' WHERE dict_type = 'djs_pig_gender';

-- 2.2 djs_breeding_type → djs_mating_method
UPDATE sys_dict_type SET dict_type = 'djs_mating_method' WHERE dict_type = 'djs_breeding_type';
UPDATE sys_dict_data SET dict_type = 'djs_mating_method' WHERE dict_type = 'djs_breeding_type';

-- 2.3 djs_out_house_dest → djs_market_dest
UPDATE sys_dict_type SET dict_type = 'djs_market_dest' WHERE dict_type = 'djs_out_house_dest';
UPDATE sys_dict_data SET dict_type = 'djs_market_dest' WHERE dict_type = 'djs_out_house_dest';

-- 2.4 djs_eliminate_reason → djs_culling_reason
UPDATE sys_dict_type SET dict_type = 'djs_culling_reason' WHERE dict_type = 'djs_eliminate_reason';
UPDATE sys_dict_data SET dict_type = 'djs_culling_reason' WHERE dict_type = 'djs_eliminate_reason';

-- 2.5 djs_eliminate_dest → djs_culling_dest
UPDATE sys_dict_type SET dict_type = 'djs_culling_dest' WHERE dict_type = 'djs_eliminate_dest';
UPDATE sys_dict_data SET dict_type = 'djs_culling_dest' WHERE dict_type = 'djs_eliminate_dest';

-- ============ 3. rename 语义碰撞分离 ============
-- djs_medicine_usage_type（领用动作 use/return/loss）→ djs_med_pick_action
UPDATE sys_dict_type SET dict_type = 'djs_med_pick_action', dict_name = '药品领用动作'
  WHERE dict_type = 'djs_medicine_usage_type';
UPDATE sys_dict_data SET dict_type = 'djs_med_pick_action' WHERE dict_type = 'djs_medicine_usage_type';
