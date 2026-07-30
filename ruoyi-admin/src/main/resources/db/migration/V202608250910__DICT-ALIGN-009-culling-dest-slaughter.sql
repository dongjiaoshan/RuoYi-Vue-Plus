-- ============================================================================
-- DICT-ALIGN-009  淘汰去向补「屠宰」
--
-- 甲方《数据补充清单·字典项确认清单》「养殖 / 淘汰去向」= 外卖 / 屠宰 / 无害化 / 其他（4 项）。
-- staging 只有 3 项（缺屠宰），淘汰母猪送屠宰这条最常用的去向选不出来。
-- dict_code 1006240 与同组另 3 项（1006241-1006243）连号，两库实查未被占用。
--
-- 跑完刷 Redis 字典缓存：本地 bash script/sql/djs/_post-init.sh
--                       staging bash ops/redis-flush-dict.sh staging --yes
-- ============================================================================
SET NAMES utf8mb4;

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
SELECT 1006240, '1001', 0, '屠宰', 'slaughter', 'djs_culling_dest', '', 'primary', 'N', NULL, NOW(), 'DICT-ALIGN-009 甲方字典清单'
 WHERE NOT EXISTS (
   SELECT 1 FROM (SELECT 1 FROM sys_dict_data
                   WHERE dict_type = 'djs_culling_dest' AND tenant_id = '1001'
                     AND dict_value = 'slaughter' LIMIT 1) x);
