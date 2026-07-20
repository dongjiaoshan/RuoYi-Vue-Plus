-- V202608160900__DICT-REFACTOR-004-aggressive-dedup.sql
-- 激进去冗余三轮（Kevin 逐组拍板）：删 11 个"开发加了但功能没真用起来"的字典。101 → 90。
-- 判据：消费列 0 有意义数据 + 功能占位/被取代/V1不做。经数据+代码+对抗验证 + 业主逐组确认。
-- 跑完必须: bash script/sql/djs/_post-init.sh 刷 Redis 字典缓存。

-- A. 退货三字典（被统一架构 t_store_return 取代，旧 t_warehouse_return_product 表 0 行）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_return_direction';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_return_direction';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_return_loss_reason';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_return_loss_reason';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_return_status';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_return_status';

-- B. 种植条件占位（218 地块全 NULL 的可选装饰字段，从没人填）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_soil_type';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_soil_type';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_soil_fertility';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_soil_fertility';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_light_condition';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_light_condition';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_terrain_condition';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_terrain_condition';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_drain_condition';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_drain_condition';

-- C. 整地分类（63 条农事全 NULL；整地已由 djs_farm_work_type 覆盖）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_tillage_type';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_tillage_type';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_tillage_way';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_tillage_way';

-- D. 会员等级（会员表 0 行，V1 不做会员）+ 隐藏会员菜单
DELETE FROM sys_dict_data WHERE dict_type = 'djs_member_level';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_member_level';
UPDATE sys_menu SET visible = '1' WHERE component LIKE 'djs-store/member/%' OR (menu_name = '会员管理' AND path LIKE '%member%');
