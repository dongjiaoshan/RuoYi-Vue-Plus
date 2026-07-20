-- ============================================================
-- FIX-614  栏位类型新增：散栏 / 保育栏
-- ============================================================
-- 现状 djs_pen_type = 大栏(big) / 限位栏(stall) / 产床(farrow)（见 BRD-AD-PROTO-ALIGN-002）。
-- 113 栋舍新增两类栏位：散栏（一栏多头散养）/ 保育栏（断奶仔猪保育）。
-- dict_value 用 'nursery_pen' 而非 'nursery'，避免与 djs_barn_type 的保育舍语义混淆。
--
-- 跑完执行 `bash script/sql/djs/_post-init.sh` flush redis 字典缓存（否则下拉读旧值）。
-- ============================================================

SET NAMES utf8mb4;

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001073, '1001', 3, '散栏',   'scatter',     'djs_pen_type', '', 'info',    'N', NULL, NOW()),
  (1001074, '1001', 4, '保育栏', 'nursery_pen', 'djs_pen_type', '', 'success', 'N', NULL, NOW());
