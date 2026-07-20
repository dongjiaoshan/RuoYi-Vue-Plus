-- ============================================================
-- BRD-EVENT-005 生长记录（GROWTH）
--   生长记录表（mp 端体重录入 + admin 端背膘/背高补录）
--   不触发状态机 — 仅录入测量数据。
--   3 天内可删（service 层 ChronoUnit.DAYS.between 校验）。
-- ============================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS t_farm_pig_growth;
CREATE TABLE t_farm_pig_growth (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  pig_id              BIGINT       NOT NULL COMMENT '猪只 ID（关联 t_farm_pig_info.id）',
  ear_no              VARCHAR(32)  NOT NULL COMMENT '猪只耳号（冗余便于查询）',
  measure_date        DATE         NOT NULL COMMENT '测量日期',
  weight              DECIMAL(12,2) NOT NULL COMMENT '体重 kg（必填，mp + admin 均录）',
  backfat_thickness   DECIMAL(5,2) NULL COMMENT '背膘厚 mm（admin 端录，专业设备）',
  back_height         DECIMAL(5,2) NULL COMMENT '背高 cm（admin 端录）',
  photo_oss_ids       VARCHAR(500) NULL COMMENT '测量照片 OSS IDs（grow_photo 业务类型，逗号分隔）',
  operator_id         BIGINT       NULL COMMENT '操作人 user_id（从 LoginUser 取）',
  barn_name           VARCHAR(64)  NULL COMMENT '栋舍名称（冗余）',
  pen_name            VARCHAR(64)  NULL COMMENT '栏位名称（冗余）',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by           BIGINT       NULL COMMENT '录入人',
  create_time         DATETIME     NULL COMMENT '录入时间',
  update_by           BIGINT       NULL COMMENT '更新人',
  update_time         DATETIME     NULL COMMENT '更新时间',
  del_flag            CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark              VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_pig_date (tenant_id, pig_id, measure_date),
  KEY idx_measure_date (measure_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猪只生长记录（BRD-EVENT-005）';
