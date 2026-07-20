-- ============================================================
-- WMS-STOCK-001 库存盘点（D11）
--
-- 1. t_warehouse_check_record DROP + CREATE 重建
--    （baseline V202605200903 占位 16 字段 AUTO_INCREMENT；对照 doc/11 §2.4 权威重建为
--     雪花主键 + 22 字段；baseline 表 0 业务数据，无迁移负担，参 WMS-MD-002 D8 范式）
-- 2. 字典复用：djs_check_status 复用 D1 V202605201000 已 seed（draft / in_progress / completed），本 ticket 不再 seed
--    （djs_check_result 1/2/3 已由 WMS-MD-001 V202605290910 seed，本 ticket 复用不重灌；
--      flow_type check_in / check_out 已由 WMS-MAT-001 V202606071300 seed，复用）
-- 3. 业务码规则 seed：CHECK_NO（格式 C{yyyyMMdd}{seq5}，每日重置）
--    BizCodeType.CHECK_NO + BizCodeGeneratorImpl 已加 {seq5} 占位符支持
-- ============================================================

SET NAMES utf8mb4;

-- ============== 1. t_warehouse_check_record 重建 ==============
DROP TABLE IF EXISTS t_warehouse_check_record;
CREATE TABLE t_warehouse_check_record (
  id                 BIGINT        NOT NULL COMMENT '主键（雪花）',
  tenant_id          VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  check_id           VARCHAR(32)   NOT NULL COMMENT '盘点单业务码 C{yyyyMMdd}{seq5}（同一盘点单的 header + 各 line 共用）',
  location_id        BIGINT        NOT NULL COMMENT 'FK t_warehouse_location_info.id（盘点是按库位的，xlsx 缺、doc/11 §2.4 推断）',
  product_id         BIGINT        NOT NULL COMMENT 'FK t_warehouse_product_info.id（header 行写 0 占位）',
  product_name       VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '产品名称（冗余）',
  product_unit       VARCHAR(16)   NOT NULL DEFAULT '' COMMENT '产品单位（冗余）',
  sys_stock          DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '盘点时系统库存量',
  check_stock        DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '实盘量',
  diff_stock         DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '差异 = check_stock - sys_stock（>0 盘盈 / <0 盘亏）',
  check_result_type  TINYINT       NOT NULL DEFAULT 1 COMMENT '字典 djs_check_result：1=正常 / 2=异常 / 3=计损',
  diff_reason        VARCHAR(255)  NULL COMMENT '差异原因',
  check_by           BIGINT        NULL COMMENT 'FK sys_user.user_id（实盘录入人；header 行可空）',
  check_date         DATETIME      NOT NULL COMMENT '盘点日期',
  check_status       VARCHAR(16)   NOT NULL DEFAULT 'draft' COMMENT '字典 djs_check_status：draft / in_progress / completed',
  is_header          TINYINT       NOT NULL DEFAULT 0 COMMENT '1=盘点单头（承载 status / 库位锁）/ 0=盘点明细 line',
  create_dept        BIGINT        NULL COMMENT '创建部门',
  create_by          BIGINT        NULL COMMENT '创建者',
  create_time        DATETIME      NULL COMMENT '创建时间',
  update_by          BIGINT        NULL COMMENT '更新者',
  update_time        DATETIME      NULL COMMENT '更新时间',
  remark             VARCHAR(500)  NULL COMMENT '备注',
  del_flag           CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志（0 存在 / 1 删除）',
  del_unique         BIGINT        NOT NULL DEFAULT 0 COMMENT '软删唯一标识',
  PRIMARY KEY (id),
  UNIQUE KEY uk_check_line (tenant_id, check_id, location_id, product_id, del_unique),
  KEY idx_location (location_id, check_status, del_flag),
  KEY idx_check_date (check_date),
  KEY idx_check_id (check_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点记录表（WMS-STOCK-001）';

-- ============== 2. 字典复用：djs_check_status 复用 D1 V202605201000 已 seed（draft / in_progress / completed），本 ticket 不再 seed ==============

-- ============== 3. 业务码规则 seed：CHECK_NO ==============
-- 格式：C{yyyyMMdd}{seq5}（每日重置，例 C2026061300001）
INSERT IGNORE INTO t_md_biz_code_rule
  (code_type,  pattern,             daily_reset, prefix, seq_length, status, create_by, create_time)
VALUES
  ('CHECK_NO', 'C{yyyyMMdd}{seq5}', 1,           'C',    5,          '0',    1,         NOW());
