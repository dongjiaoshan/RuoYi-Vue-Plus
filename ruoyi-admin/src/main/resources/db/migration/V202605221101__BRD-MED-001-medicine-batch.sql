-- ============================================================
-- BRD-MED-001 药品库 + 药品批次
--
-- 现状：
--   表 t_breed_medicine_info 已在 SYS-INIT-001 V202605200901 建好（含
--     medicine_code/name/type/supplier_id/approval_no/batch_no/expire_date/
--     withdraw_days/unit/current_stock + 审计 + del_flag + del_unique）。
--   prompt 数据模型段叫 t_farm_medicine，与 DB / doc/06 / _db-changes 已落地的
--   t_breed_medicine_info 不一致；本 ticket 按 DB + doc/06 走（详 D04 _open-issues raise）。
--
-- 本文件做 3 件事：
--   1. 给 t_breed_medicine_info 加 4 列：spec / manufacturer / storage_condition / med_status
--   2. 建批次表 t_breed_medicine_batch（V1 admin 暴露批次能力；V2 BRD-MED-002 FIFO 扣减用）
--   3. seed 菜单：父 7000(养殖) 下二级 7040(药品库) + 7050(药品批次) + 子按钮权限
--      （djs_med_type 字典已在 SYS-INIT-002 V202605201000 seed，无需重做）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 给 t_breed_medicine_info 增列（spec / manufacturer / storage_condition / med_status）
--    用 INFORMATION_SCHEMA 探测列存在性，可重入；同 D02-PATCH 风格
-- ------------------------------------------------------------
SET @schema := DATABASE();
SET @tbl := 't_breed_medicine_info';

-- spec
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = @tbl AND COLUMN_NAME = 'spec');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_breed_medicine_info ADD COLUMN spec VARCHAR(128) NULL COMMENT ''规格（如 10ml × 100 支 / 盒）'' AFTER current_stock',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- manufacturer
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = @tbl AND COLUMN_NAME = 'manufacturer');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_breed_medicine_info ADD COLUMN manufacturer VARCHAR(128) NULL COMMENT ''生产厂家'' AFTER spec',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- storage_condition
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = @tbl AND COLUMN_NAME = 'storage_condition');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_breed_medicine_info ADD COLUMN storage_condition VARCHAR(200) NULL COMMENT ''储存条件（如 2-8℃ 冷藏 避光）'' AFTER manufacturer',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- med_status
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = @tbl AND COLUMN_NAME = 'med_status');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_breed_medicine_info ADD COLUMN med_status TINYINT NOT NULL DEFAULT 1 COMMENT ''状态 1=启用 0=停用（对齐 sys_normal_disable）'' AFTER storage_condition',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ------------------------------------------------------------
-- 2. 建批次表 t_breed_medicine_batch（BRD-MED-001 批次能力，V2 FIFO 扣减用）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_breed_medicine_batch;
CREATE TABLE t_breed_medicine_batch (
  id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  medicine_id       BIGINT       NOT NULL COMMENT '药品 ID（引用 t_breed_medicine_info.id）',
  batch_no          VARCHAR(64)  NOT NULL COMMENT '批次编码',
  production_date   DATE         NULL COMMENT '生产日期',
  expiry_date       DATE         NULL COMMENT '过期日期',
  quantity          DECIMAL(18,3) NOT NULL DEFAULT 0 COMMENT '批次当前剩余库存',
  unit_price        DECIMAL(18,2) NULL COMMENT '进货单价',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '创建人',
  create_time       DATETIME     NULL COMMENT '创建时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  del_unique        BIGINT       NOT NULL DEFAULT 0 COMMENT "软删 token（应用层 update del_flag='1' 时同步 SET del_unique=id）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_med_batch (tenant_id, medicine_id, batch_no, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_medicine (medicine_id),
  KEY idx_expiry (expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药品批次表（BRD-MED-001）';


-- ------------------------------------------------------------
-- 3. 菜单 seed（父 7000=养殖 已有；二级 7060 药品库 / 7070 药品批次）
--    分段说明：BRD-MD-001 占 7010-7015（育种）/ BRD-MD-002 占 7020-7043
--    （农场 7020-7022 + 栋舍 7030-7033 + 栏位 7040-7043）
--    本 ticket 跳过 7040-7059 段避免 BRD-MD-002 扩展冲突；用 7060-7079
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 二级目录：药品库
  (7060, '药品库', 7000, 4, 'med', 'djs-breed/med/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:med:list', 'medication', 1, NOW(), 'BRD-MED-001'),

  -- 子按钮权限（药品库 5 个，与后端 @SaCheckPermission 严格一致）
  (7061, '药品查询', 7060, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med:list',   '#', 1, NOW(), 'BRD-MED-001'),
  (7062, '药品新增', 7060, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med:add',    '#', 1, NOW(), 'BRD-MED-001'),
  (7063, '药品修改', 7060, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med:edit',   '#', 1, NOW(), 'BRD-MED-001'),
  (7064, '药品删除', 7060, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med:remove', '#', 1, NOW(), 'BRD-MED-001'),
  (7065, '药品导出', 7060, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med:export', '#', 1, NOW(), 'BRD-MED-001'),

  -- 二级目录：药品批次
  (7070, '药品批次', 7000, 5, 'med-batch', 'djs-breed/med/batch', '',
   1, 0, 'C', '0', '0',
   'djs:breed:med-batch:list', 'list', 1, NOW(), 'BRD-MED-001'),

  -- 子按钮权限（药品批次 5 个）
  (7071, '批次查询', 7070, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med-batch:list',   '#', 1, NOW(), 'BRD-MED-001'),
  (7072, '批次新增', 7070, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med-batch:add',    '#', 1, NOW(), 'BRD-MED-001'),
  (7073, '批次修改', 7070, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med-batch:edit',   '#', 1, NOW(), 'BRD-MED-001'),
  (7074, '批次删除', 7070, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med-batch:remove', '#', 1, NOW(), 'BRD-MED-001'),
  (7075, '批次导出', 7070, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med-batch:export', '#', 1, NOW(), 'BRD-MED-001');
