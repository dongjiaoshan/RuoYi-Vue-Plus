-- ============================================================================
-- PLT-CROP-GENUS-001  作物「科属」拆两列：t_plant_crop_info 新增 crop_genus（属）
--
-- 甲方作物信息里「作物科属」是「十字花科 芸薹属」这种科 + 属复合串：
--   科（crop_family）继续走字典 djs_crop_family（可按科筛作物 / 导出中文）；
--   属（crop_genus）取值发散且甲方明确要「录入段」，做自由文本，不进字典。
--
-- 幂等：MySQL 无 ADD COLUMN IF NOT EXISTS，按项目既有先例用 information_schema 存在性判断
--   后再 ADD（见 V202606291100__DJS-FIX-WMS-PACK-deliver-dest-dict.sql）。
-- ============================================================================
SET NAMES utf8mb4;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_plant_crop_info'
      AND COLUMN_NAME = 'crop_genus'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_plant_crop_info ADD COLUMN crop_genus VARCHAR(32) NULL COMMENT ''作物属（自由文本，如 芸薹属）'' AFTER crop_family',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
