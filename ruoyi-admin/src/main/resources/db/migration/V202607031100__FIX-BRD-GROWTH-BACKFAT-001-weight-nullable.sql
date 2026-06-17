-- FIX-BRD-GROWTH-BACKFAT-001 — 生长记录 weight 列改可空（邓博 2026-06-17 #35）。
-- 背景：mp 端「生长记录（录入体测）」由录【体重 kg】改为录【背膘厚度 mm】，mp 不再传 weight。
--   t_farm_pig_growth.weight 原 NOT NULL，mp 提交不带 weight 会 INSERT 失败。
--   weight 改 NULL 后：mp 录 backfat_thickness（前端必填）、admin 端按需录 weight（各端前端校验）。
-- 同步去掉 GrowthBo.weight 的 @NotNull（后端两端 BO 共用，校验交各端前端）。
-- V202607031100 > 当前 flyway max V202607031000。

ALTER TABLE t_farm_pig_growth
    MODIFY COLUMN weight DECIMAL(12, 2) NULL COMMENT '体重 kg（可选，admin 端录）';
