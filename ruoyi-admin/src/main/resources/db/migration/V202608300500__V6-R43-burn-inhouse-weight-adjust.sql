-- V6-R43 燎毛间产品重量调整：t_warehouse_product_inhouse 补「所属燎毛记录」+「调整留痕」四列
--
-- 一次燎毛间产品入库（PigBurnRecordServiceImpl#submitBurnRecord）会同时写 4 张表，
-- 「产品入库重量」是其中每一处数值的来源：
--   t_warehouse_product_inhouse.product_weight                        = 该产品本次入库重量（本行）
--   t_warehouse_location_stock.product_stock                          = 同上（按 white_bar_no 一一对应）
--   t_warehouse_stock_flow.change_num / change_quantity               = 同上（slaughter_burn + IN，按 white_bar_no 一一对应）
--   t_warehouse_pig_burn_record.burn_weight                           = 本次提交各产品重量之和
-- 前三张靠 white_bar_no 能精确一一对上，唯独 burn_record 没有任何外键指回产出行 ——
-- burn_record_id 补上这一环，让重量调整能确定性地同步「燎毛记录」的合计重量，
-- 同时给列表「入库人」提供权威来源（入库人 = burn_record.operator_id，由 EmployeePicker 指定，可与登录人不同）。

-- 幂等：MySQL 无 ADD COLUMN IF NOT EXISTS，按项目既有先例用 information_schema 判存在
-- （见 V202608241300__PLT-CROP-GENUS-001 / V202608300300__V6-R36-planting-change-type）。
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_warehouse_product_inhouse'
      AND COLUMN_NAME = 'burn_record_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_warehouse_product_inhouse
        ADD COLUMN burn_record_id BIGINT   NULL COMMENT ''所属燎毛记录 FK → t_warehouse_pig_burn_record.id（仅燎毛间入库产出行有值）'',
        ADD COLUMN is_adjusted    TINYINT  NOT NULL DEFAULT 0 COMMENT ''入库重量是否被调整过（字典 djs_yes_no：1=是 / 0=否）'',
        ADD COLUMN adjust_time    DATETIME NULL COMMENT ''最近一次入库重量调整时间'',
        ADD COLUMN adjust_by      BIGINT   NULL COMMENT ''最近一次入库重量调整人 FK → sys_user.user_id''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_warehouse_product_inhouse'
      AND INDEX_NAME = 'idx_burn_record'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE t_warehouse_product_inhouse ADD KEY idx_burn_record (tenant_id, burn_record_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史行回填 burn_record_id。
-- 燎毛产出行的结构判据 = white_bar_id 非空（分割/领用 WIP 行不写它）
--   + material_id 为空（物资/白条领用产生的 WIP 行 material_id 恒非空）
--   + source='warehouse'（排除门店拆单 source='store' 行）。
-- submitBurnRecord 把同一个 bo.burnTime 同时写进 inhouse.produce_time 与 burn_record.burn_time，
-- 故 (ear_no, 该时刻) 是这批历史行与燎毛记录的精确对应关系；外购白条 ear_no 为 NULL → 用 NULL 安全比较 <=>。
UPDATE t_warehouse_product_inhouse ih
  JOIN t_warehouse_pig_burn_record b
    ON b.del_flag = '0'
   AND b.tenant_id = ih.tenant_id
   AND b.ear_no <=> ih.ear_no
   AND b.burn_time = ih.produce_time
   SET ih.burn_record_id = b.id
 WHERE ih.burn_record_id IS NULL
   AND ih.white_bar_id IS NOT NULL
   AND ih.material_id IS NULL
   AND ih.source = 'warehouse';
