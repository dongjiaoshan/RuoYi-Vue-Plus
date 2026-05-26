-- ============================================================
-- D06 closing #6 配种 / 分娩 加 proof_oss_ids 凭证图字段（对齐 PigIntroBo 模式）
--
-- 触发：testing-human #5 closing — mp 拍照上传组件已采集 ossId 但 POST 不带，
-- 导致凭证图 dead-end（OSS bucket 有文件，业务表无关联）。
--
-- 与 t_farm_pig_introduce.proof_oss_ids 字段对齐（VARCHAR(1024) NULL）。
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE t_farm_pig_breeding
  ADD COLUMN proof_oss_ids VARCHAR(1024) NULL COMMENT '凭证图片 OSS IDs 逗号分隔（配种现场照片）' AFTER pen_name;

ALTER TABLE t_farm_pig_farrow
  ADD COLUMN proof_oss_ids VARCHAR(1024) NULL COMMENT '凭证图片 OSS IDs 逗号分隔（分娩现场照片）' AFTER pen_name;
