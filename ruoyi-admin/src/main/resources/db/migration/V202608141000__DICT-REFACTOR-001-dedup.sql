-- V202608141000__DICT-REFACTOR-001-dedup.sql
-- 字典重构（安全版）：删 21 冗余类型 + 清 1 组孤儿数据。不做编码翻转（product/med/farm/zone 保持现状）。
-- 合并：djs_active_status / djs_location_status 的代码消费方已 repoint 到 djs_common_status（值集同为 1/2）。
-- djs_sow_status 直删（值集与 djs_pig_lifecycle 不相交，非映射）。
-- 跑完必须: bash script/sql/djs/_post-init.sh 刷 Redis 字典缓存。

-- A. 死字典(13)
DELETE FROM sys_dict_data WHERE dict_type = 'djs_crop_type';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_crop_type';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_dispatch_priority';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_dispatch_priority';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_med_event_trigger';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_med_event_trigger';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_organic_cert_status';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_organic_cert_status';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_pack_type';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_pack_type';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_product_status';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_product_status';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_return_reason';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_return_reason';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_subscribe_message_type';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_subscribe_message_type';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_task_biz';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_task_biz';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_task_status';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_task_status';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_trace_event_type';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_trace_event_type';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_in_type';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_in_type';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_death_kind';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_death_kind';

-- B. 被更新字典取代(3)
DELETE FROM sys_dict_data WHERE dict_type = 'djs_demand_business';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_demand_business';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_stock_flow_type';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_stock_flow_type';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_user_status';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_user_status';

-- C. 合并源/直删(5)：active/location 已 repoint 到 djs_common_status；role_code 走 sys_role；sow_status 死支路；store_pig 脏数据
DELETE FROM sys_dict_data WHERE dict_type = 'djs_active_status';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_active_status';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_location_status';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_location_status';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_role_code';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_role_code';
DELETE FROM sys_dict_data WHERE dict_type = 'djs_sow_status';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_sow_status';
DELETE FROM sys_dict_data WHERE dict_type = 'store_pig';
DELETE FROM sys_dict_type WHERE dict_type = 'store_pig';

-- D. 孤儿 dict_data（无 sys_dict_type 表头，被 djs_mat_type 取代）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_material_type';
