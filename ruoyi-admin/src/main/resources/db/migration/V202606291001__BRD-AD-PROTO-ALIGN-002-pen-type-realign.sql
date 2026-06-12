-- ============================================================
-- BRD-AD-PROTO-ALIGN-002  栏位类型对齐原型：大栏 / 限位栏 / 产床
-- ============================================================
-- 原型「农场栋舍管理」把栏位分三类：大栏（一栏多头记头数）/ 限位栏（单头母猪记耳号）/
-- 产床（单头母猪 + 仔猪数）。新建栋舍时按三类数量自动批量生成栏位。
--
-- 旧字典 djs_pen_type = 公栏(male)/母栏(female)/限位栏(stall)/群栏(group)，与原型不符。
-- 本文件把字典重定义为 大栏(big)/限位栏(stall)/产床(farrow) 三值，并迁移已有栏位数据：
--   旧 male/female/group → 大栏 big；旧 stall 保留为 限位栏 stall。
--
-- 跑完执行 `bash script/sql/djs/_post-init.sh` flush redis 字典缓存（否则下拉读旧值）。
-- ============================================================

SET NAMES utf8mb4;

-- 1. 字典 djs_pen_type 重定义为三值（大栏 / 限位栏 / 产床）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_pen_type';
INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001070, '1001', 0, '大栏',   'big',    'djs_pen_type', '', 'primary', 'N', NULL, NOW()),
  (1001071, '1001', 1, '限位栏', 'stall',  'djs_pen_type', '', 'warning', 'N', NULL, NOW()),
  (1001072, '1001', 2, '产床',   'farrow', 'djs_pen_type', '', 'success', 'N', NULL, NOW());

-- 2. 迁移已有栏位数据到新三类（公栏/母栏/群栏 → 大栏；限位栏 stall 保持）
UPDATE t_farm_barn_pen SET pen_type = 'big' WHERE pen_type IN ('male', 'female', 'group');
