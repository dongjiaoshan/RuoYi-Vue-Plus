-- ============================================================================
-- DICT-ALIGN-001  栋舍类型补齐（断配舍 / 功能舍 / 配分舍）+ 默认仔猪重量兜底
--
-- 背景：甲方「猪舍信息」19 栋用到 断配舍(H008) / 功能舍(H007) / 配分舍(H010、H012)，
--   djs_barn_type 只有 6 项。初始数据生成器按中文 label 查目标库 sys_dict_data 映射成 value，
--   字典缺项 → 这 4 栋灌不进去。
--
-- 取号：养殖字典 1001xxx 段，续 1001060-1001065 取 1001066-1001068（两库实查为空）。
--
-- 幂等写法：按 dict_value 定点 DELETE 再 INSERT。sys_dict_data.dict_code 只是主键，
--   没有任何业务表拿它做外键（代码一律按 dict_type + dict_value 取值），换号安全。
--   本地 DELETE 是空操作；staging 上 功能舍 / 配分舍 是在 admin 手工加的雪花 dict_code 行，
--   会被收编成低位号 —— 两库不同起点、同一终点，重跑也幂等。
--
-- djs_piglet_default_weight：mp 耳标标记页「出生重」、断奶录入页「断奶重」的预填默认值，
--   代码在用。INSERT IGNORE 兜底，保证两库都有。
--
-- 跑完刷 Redis 字典缓存：bash script/sql/djs/_post-init.sh
-- ============================================================================
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. 栋舍类型 djs_barn_type：断配舍 / 功能舍 / 配分舍
-- ----------------------------------------------------------------------------
DELETE FROM sys_dict_data
 WHERE dict_type = 'djs_barn_type'
   AND dict_value IN ('duan_pei', 'gong_neng', 'pei_fen');

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
  (1001066, '1001', 6, '断配舍', 'duan_pei',  'djs_barn_type', '', 'primary', 'N', NULL, NOW(), '甲方猪舍信息 H008 断配舍8栋'),
  (1001067, '1001', 7, '功能舍', 'gong_neng', 'djs_barn_type', '', 'primary', 'N', NULL, NOW(), '甲方猪舍信息 H007 功能舍7栋：产床 + 限位栏 + 保育'),
  (1001068, '1001', 8, '配分舍', 'pei_fen',   'djs_barn_type', '', 'primary', 'N', NULL, NOW(), '甲方猪舍信息 H010、H012 配分舍：产床 + 限位栏');

-- ----------------------------------------------------------------------------
-- 2. 默认仔猪重量 djs_piglet_default_weight（dict_value 存 kg 数值，客户可在 admin 改）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (103300, '1001', '默认仔猪重量', 'djs_piglet_default_weight', NULL, NOW(), '养殖：仔猪录入默认重量（kg），出生重预填耳标页、断奶重预填断奶页，客户可改');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1033000, '1001', 0, '仔猪出生重', '2', 'djs_piglet_default_weight', '', 'primary', 'Y', NULL, NOW()),
  (1033001, '1001', 1, '仔猪断奶重', '5', 'djs_piglet_default_weight', '', 'success', 'N', NULL, NOW());
