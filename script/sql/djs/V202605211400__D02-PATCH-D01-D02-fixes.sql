-- ============================================================
-- D02 当日修订补丁（D01 推断字段 + D01/D02 字典与 DDL 不自洽 修订）
--   触发：D02 全栈 review 提出
--     1. t_md_store / t_farm_pig_info 4 个 AI 推断字段不符合实际业务
--     2. djs_pig_lifecycle / djs_demand_status dict_value 与 DDL enum 不一致
--     3. djs_check_status 字典缺失（DDL 已引用）
--   修补范围（与重新跑全量初始化 SQL 后结果等价）：
--     A. ALTER DROP 4 个推断字段
--     B. 重写 djs_pig_lifecycle 10 行 dict_data（HB/PZ/PH/FM/DN/LC/KH/FQ/END + BOAR_ACTIVE）
--     C. 修正 djs_demand_status 2 行 dict_value（SCHEDULING→IN_PRODUCTION / PARTIAL→PARTIAL_SHIPPED）
--     D. 新增 djs_check_status 字典 + 3 行 dict_data
--   幂等性：
--     - DROP COLUMN 用 information_schema 守卫，已删则跳过（MySQL 8 无 DROP IF EXISTS COLUMN）
--     - dict_data 用 DELETE + INSERT 重写
--     - dict_type 用 INSERT IGNORE（已存在则跳过）
--   重跑场景：
--     - 已 drop & rebuild dev DB（源 SQL 已修）→ 本 patch 全 no-op
--     - 沿用现 dev DB → 本 patch 完成迁移
-- ============================================================

-- ------------------------------------------------------------
-- A. ALTER DROP 4 个推断字段（D02 review 决策：删）
-- ------------------------------------------------------------

-- A1. t_md_store.warehouse_id：客户 V1 无门店专属仓库需求
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_md_store' AND COLUMN_NAME = 'warehouse_id');
SET @sql := IF(@col > 0, 'ALTER TABLE t_md_store DROP COLUMN warehouse_id', 'SELECT ''A1 skip: t_md_store.warehouse_id absent'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- A2. t_md_store.settle_type：客户 V1 无门店结算需求
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_md_store' AND COLUMN_NAME = 'settle_type');
SET @sql := IF(@col > 0, 'ALTER TABLE t_md_store DROP COLUMN settle_type', 'SELECT ''A2 skip: t_md_store.settle_type absent'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- A3. t_farm_pig_info.current_weight：无日常称重事件源，应改走 t_farm_pig_weight_record（V2 引入）
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_farm_pig_info' AND COLUMN_NAME = 'current_weight');
SET @sql := IF(@col > 0, 'ALTER TABLE t_farm_pig_info DROP COLUMN current_weight', 'SELECT ''A3 skip: t_farm_pig_info.current_weight absent'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- A4. t_farm_pig_info.current_age_days：VO 层从 birth_date 实时算（DATEDIFF），落库冗余
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_farm_pig_info' AND COLUMN_NAME = 'current_age_days');
SET @sql := IF(@col > 0, 'ALTER TABLE t_farm_pig_info DROP COLUMN current_age_days', 'SELECT ''A4 skip: t_farm_pig_info.current_age_days absent'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- B. 重写 djs_pig_lifecycle 字典 dict_value（与 BRD-CORE-001 PigState enum 严格对齐）
--    DDL t_farm_pig_info.current_status DEFAULT 'HB' / 9 枚举：HB/PZ/PH/FM/DN/LC/KH/FQ/END
--    + 公猪固定 BOAR_ACTIVE（不在 9 状态内，但需 enum 字段非空）
-- ------------------------------------------------------------
DELETE FROM sys_dict_data WHERE dict_type = 'djs_pig_lifecycle';
INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001030, '1001', 0, '后备',     'HB',          'djs_pig_lifecycle', '', 'info',    'Y', NULL, NOW()),
  (1001031, '1001', 1, '配种',     'PZ',          'djs_pig_lifecycle', '', 'primary', 'N', NULL, NOW()),
  (1001032, '1001', 2, '配怀',     'PH',          'djs_pig_lifecycle', '', 'primary', 'N', NULL, NOW()),
  (1001033, '1001', 3, '分娩',     'FM',          'djs_pig_lifecycle', '', 'success', 'N', NULL, NOW()),
  (1001034, '1001', 4, '断奶',     'DN',          'djs_pig_lifecycle', '', 'success', 'N', NULL, NOW()),
  (1001035, '1001', 5, '流产',     'LC',          'djs_pig_lifecycle', '', 'warning', 'N', NULL, NOW()),
  (1001036, '1001', 6, '空怀',     'KH',          'djs_pig_lifecycle', '', 'warning', 'N', NULL, NOW()),
  (1001037, '1001', 7, '返情',     'FQ',          'djs_pig_lifecycle', '', 'warning', 'N', NULL, NOW()),
  (1001038, '1001', 8, '终止',     'END',         'djs_pig_lifecycle', '', 'danger',  'N', NULL, NOW()),
  (1001039, '1001', 9, '公猪在产', 'BOAR_ACTIVE', 'djs_pig_lifecycle', '', 'info',    'N', NULL, NOW());

-- ------------------------------------------------------------
-- C. 修正 djs_demand_status 2 行 dict_value（与 DDL t_warehouse_demand_manage.demand_status enum 对齐）
-- ------------------------------------------------------------
UPDATE sys_dict_data SET dict_value = 'IN_PRODUCTION'
  WHERE dict_type = 'djs_demand_status' AND dict_value = 'SCHEDULING';
UPDATE sys_dict_data SET dict_value = 'PARTIAL_SHIPPED'
  WHERE dict_type = 'djs_demand_status' AND dict_value = 'PARTIAL';

-- ------------------------------------------------------------
-- D. 新增 djs_check_status 字典（盘点状态，跨域 warehouse + store）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100601, '1001', '盘点状态', 'djs_check_status', NULL, NOW(), '跨域：t_warehouse_check_record / t_store_check_record.check_status');
DELETE FROM sys_dict_data WHERE dict_type = 'djs_check_status';
INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006010, '1001', 0, '草稿',   'draft',       'djs_check_status', '', 'info',    'Y', NULL, NOW()),
  (1006011, '1001', 1, '进行中', 'in_progress', 'djs_check_status', '', 'warning', 'N', NULL, NOW()),
  (1006012, '1001', 2, '已完成', 'completed',   'djs_check_status', '', 'success', 'N', NULL, NOW());

-- ============================================================
-- 验收（dev MySQL 跑完后预期）：
--   SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
--     AND ((TABLE_NAME='t_md_store' AND COLUMN_NAME IN ('warehouse_id','settle_type'))
--          OR (TABLE_NAME='t_farm_pig_info' AND COLUMN_NAME IN ('current_weight','current_age_days')));
--   -- 0
--   SELECT COUNT(*) FROM sys_dict_type WHERE dict_type LIKE 'djs_%';    -- 39
--   SELECT COUNT(*) FROM sys_dict_data WHERE dict_type LIKE 'djs_%';    -- 228（旧 224 - 9 老 lifecycle + 10 新 + 3 check_status）
--   SELECT dict_value FROM sys_dict_data WHERE dict_type='djs_pig_lifecycle' ORDER BY dict_sort;
--   -- HB,PZ,PH,FM,DN,LC,KH,FQ,END,BOAR_ACTIVE
--   SELECT dict_value FROM sys_dict_data WHERE dict_type='djs_demand_status' ORDER BY dict_sort;
--   -- DRAFT,SUBMITTED,CONFIRMED,IN_PRODUCTION,PARTIAL_SHIPPED,COMPLETED,CANCELLED
-- ============================================================
