-- V202608151000__DICT-REFACTOR-003-strict-dedup.sql
-- 严格判据二轮去冗余：删 13 个"有引用但无活功能真在用"的字典。
-- 判据：消费表不存在 / 消费列 0 数据且无活 UI / 被表或硬编码取代。均经数据+代码+对抗验证。
-- 保留 djs_farm_status 作 V2 多农场储备（ADR-0001 defer）。
-- 跑完必须: bash script/sql/djs/_post-init.sh 刷 Redis 字典缓存。

-- 被表取代（数据源/翻译已移到业务表）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_pig_breed';    -- → t_farm_breed_info
DELETE FROM sys_dict_type WHERE dict_type = 'djs_pig_breed';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_pig_strain';   -- → t_farm_breed_info
DELETE FROM sys_dict_type WHERE dict_type = 'djs_pig_strain';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_crop';         -- → t_plant_crop_info
DELETE FROM sys_dict_type WHERE dict_type = 'djs_crop';

-- 消费表不存在（功能未建）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_warehouse_type';  -- t_warehouse_house 不存在
DELETE FROM sys_dict_type WHERE dict_type = 'djs_warehouse_type';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_dispatch_stage';  -- 派工表不存在
DELETE FROM sys_dict_type WHERE dict_type = 'djs_dispatch_stage';

-- 硬编码/裸字段取代（字典从不被翻译）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_bar_in_method';   -- 代码硬写恒=1
DELETE FROM sys_dict_type WHERE dict_type = 'djs_bar_in_method';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_bar_out_method';  -- 裸字段无 @ExcelDictFormat
DELETE FROM sys_dict_type WHERE dict_type = 'djs_bar_out_method';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_pack_send_dest';  -- 硬编码 i18n
DELETE FROM sys_dict_type WHERE dict_type = 'djs_pack_send_dest';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_medicine_unit';   -- mp 硬编码 picker
DELETE FROM sys_dict_type WHERE dict_type = 'djs_medicine_unit';

-- 养殖繁殖子功能未通过字典录入（列 0 数据 / 恒单值，mp 硬编码）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_mating_method';   -- 配种恒'本场公猪'
DELETE FROM sys_dict_type WHERE dict_type = 'djs_mating_method';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_semen';           -- 精液码 0 数据
DELETE FROM sys_dict_type WHERE dict_type = 'djs_semen';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_heat_result';     -- 查情结果全 NULL
DELETE FROM sys_dict_type WHERE dict_type = 'djs_heat_result';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_null_return';     -- 返空原因全 NULL
DELETE FROM sys_dict_type WHERE dict_type = 'djs_null_return';
