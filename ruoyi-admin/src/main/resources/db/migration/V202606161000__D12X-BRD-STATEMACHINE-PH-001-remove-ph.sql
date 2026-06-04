-- D12X-BRD-STATEMACHINE-PH-001 删配怀状态 PH（ADR-0010 修订 BRD-CORE-001 母猪状态机）
-- 1. 删 djs_pig_lifecycle 字典『配怀』行（按 dict_value 删，幂等）
DELETE FROM sys_dict_data
 WHERE dict_type = 'djs_pig_lifecycle' AND dict_value = 'PH';

-- 2. 现存 current_status='PH' 数据迁移：配怀 → 配种态（业务等价"仍待分娩"）
UPDATE t_farm_pig_info
   SET current_status = 'PZ'
 WHERE current_status = 'PH' AND del_flag = '0';
