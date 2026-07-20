-- ============================================================
-- F1FIX-2  字典死值收口（审计 DRIFT-4 / DRIFT-5）
-- ============================================================
-- 1. djs_demand_status 的 IN_PRODUCTION：状态机已无该转移（需求确认后由发货链路
--    直接推进），但历史 audit_history 仍可能含 IN_PRODUCTION 转移记录、历史弹窗
--    要靠本行渲染 label —— 不物理删。sys_dict_data 无 status/visible 列，无法
--    对下拉隐藏，采用 label 加「(已废弃)」+ list_class 灰显（同 DELETED 行惯例）。
-- 2. djs_produce_location 孤儿字典整组删除：t_warehouse_product_production
--    .produce_location 已是 BIGINT 库位 FK（存 location_info.id），后端 / plus-ui
--    / miniapp / trace-h5 对该 dict_type 零引用，仅 seed 自身残留。
-- 跑完需 flush redis 字典缓存（script/sql/djs/_post-init.sh），否则 1h 内仍回旧值。
-- ============================================================
SET NAMES utf8mb4;

UPDATE sys_dict_data
SET dict_label = '排产中(已废弃)',
    list_class = 'info'
WHERE dict_type = 'djs_demand_status'
  AND dict_value = 'IN_PRODUCTION';

DELETE FROM sys_dict_data WHERE dict_type = 'djs_produce_location';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_produce_location';
