-- ============================================================================
-- DICT-ALIGN-011  片区「所属大区」字典补数据
--
-- V202606260021__FIX-PLT-AD-PLOT-001 建了 djs_zone_belong 的 dict_type 空壳，
-- 注释写「待客户提供清单后补 sys_dict_data」。甲方这轮在「土地信息 (更新)」的
-- 『所属大区』列给全了（161/161 有值：一期基地 89 / 二期基地 72，33 个片区各属其一），
-- 客户也已在 staging admin 自建了这两项（value = A / B），本迁移把它固化进 Flyway：
--   · staging 已有 A/B（雪花 dict_code）→ 按 dict_value 判存在，跳过，不动客户的行。
--   · 本地缺 → 用 canonical 低位号 1002580-1002581 插入，value 与 staging 完全一致。
-- 灌数侧配套：gen_plant.py 把该列映射成 t_plant_plot_zone.zone_belong（此前整列 NULL，
-- admin 地块列表「所属大区」列 / 片区表单 / 种植计划向导的大区分组全空）。
--
-- 跑完刷 Redis 字典缓存：本地 bash script/sql/djs/_post-init.sh
--                       staging bash ops/redis-flush-dict.sh staging --yes
-- ============================================================================
SET NAMES utf8mb4;

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
SELECT 1002580, '1001', 0, '一期基地', 'A', 'djs_zone_belong', '', 'primary', 'N', NULL, NOW(), 'DICT-ALIGN-011 甲方土地表所属大区'
 WHERE NOT EXISTS (
   SELECT 1 FROM (SELECT 1 FROM sys_dict_data
                   WHERE dict_type = 'djs_zone_belong' AND tenant_id = '1001'
                     AND dict_value = 'A' LIMIT 1) x);

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
SELECT 1002581, '1001', 1, '二期基地', 'B', 'djs_zone_belong', '', 'success', 'N', NULL, NOW(), 'DICT-ALIGN-011 甲方土地表所属大区'
 WHERE NOT EXISTS (
   SELECT 1 FROM (SELECT 1 FROM sys_dict_data
                   WHERE dict_type = 'djs_zone_belong' AND tenant_id = '1001'
                     AND dict_value = 'B' LIMIT 1) x);
