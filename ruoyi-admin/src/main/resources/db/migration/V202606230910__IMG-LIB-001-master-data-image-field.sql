-- ============================================================
-- IMG-LIB-001 主数据加主图字段（4 层 resolver L1）
--   t_plant_crop_info        + image_oss_id / image_source
--   t_warehouse_product_info + image_oss_id / image_source
--   image_oss_id : 主图 ossId（write-time 自动匹配存入 / 用户手改存入）。
--   image_source : 0 自动匹配 / 1 手动改过（rematch 只重跑 source=0 的行）。
--   迁移现有单图缩略 ossId（crop_image_preview / product_thumb）进 image_oss_id。
--   多图原图 crop_image_url / product_img 不动。
-- ============================================================

ALTER TABLE t_plant_crop_info
    ADD COLUMN image_oss_id VARCHAR(32) NULL COMMENT '主图 ossId（4 层 resolver L1，sys_oss.oss_id）',
    ADD COLUMN image_source TINYINT NOT NULL DEFAULT 0 COMMENT '图来源（0 自动匹配 / 1 手动）';

ALTER TABLE t_warehouse_product_info
    ADD COLUMN image_oss_id VARCHAR(32) NULL COMMENT '主图 ossId（4 层 resolver L1，sys_oss.oss_id）',
    ADD COLUMN image_source TINYINT NOT NULL DEFAULT 0 COMMENT '图来源（0 自动匹配 / 1 手动）';

-- 迁移：crop_image_preview seed 里存的就是 19 位 ossId。
UPDATE t_plant_crop_info
SET image_oss_id = crop_image_preview
WHERE crop_image_preview IS NOT NULL AND crop_image_preview <> '';

-- 迁移：product_thumb 是单 ossId。
UPDATE t_warehouse_product_info
SET image_oss_id = product_thumb
WHERE product_thumb IS NOT NULL AND product_thumb <> '';
