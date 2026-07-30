-- ============================================================================
-- DICT-ALIGN-010  灾害类型 label 对齐甲方用词
--
-- 甲方《数据补充清单·字典项确认清单》「种植 / 灾害类型」= 虫害 / 草害 / 风灾 / 洪灾 / 旱灾 / 病害。
-- 库里两项用词不同：洪涝 → 洪灾、干旱 → 旱灾。dict_value（flood / drought）不动，
-- 业务表存的是 value，改 label 只影响显示。
--
-- 跑完刷 Redis 字典缓存：本地 bash script/sql/djs/_post-init.sh
--                       staging bash ops/redis-flush-dict.sh staging --yes
-- ============================================================================
SET NAMES utf8mb4;

UPDATE sys_dict_data SET dict_label = '洪灾', update_time = NOW()
 WHERE dict_type = 'djs_disaster_type' AND tenant_id = '1001' AND dict_value = 'flood';

UPDATE sys_dict_data SET dict_label = '旱灾', update_time = NOW()
 WHERE dict_type = 'djs_disaster_type' AND tenant_id = '1001' AND dict_value = 'drought';
