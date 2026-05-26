-- BRD-DASH-001-be: 给 4 张养殖聚合表补契约 3 必含字段（del_flag / del_unique / 5 审计字段）+ UNIQUE
-- 4 表已在 SYS-INIT-001 baseline 创建；本 migration 仅做 ALTER（不破坏现有 BRD-LIST-001 SowPerformance domain 字段）。
--
-- 契约依据：.claude/skills/coder-djs-cross-layer-contract.md §契约 3
-- 4 张表：
--   t_farm_sow_record           母猪日数据汇总（活动统计 7d 数据源）
--   t_farm_sow_performance      母猪生产指标（BRD-LIST-001 详情 tab2 数据源；本 ticket 由聚合 job 写入）
--   t_farm_monthly_production   月度生产数据（红/绿/黑对比数据源）
--   t_farm_annual_indicator     年度指标
--
-- 全部 ALTER 用 INFORMATION_SCHEMA 判断幂等（MySQL ALTER ADD COLUMN 不支持 IF NOT EXISTS）。
-- baseline 已有 create_time，无需补；这里只补 5 个其他审计字段 + del_flag/del_unique。

DELIMITER $$

DROP PROCEDURE IF EXISTS pr_brd_dash_001_align_audit$$
CREATE PROCEDURE pr_brd_dash_001_align_audit()
BEGIN
  DECLARE v_db VARCHAR(64) DEFAULT DATABASE();

  -- 内嵌：按 (table, column, ddl) 检查并执行
  -- column 不存在则跑 ddl
  -- ============================================================
  -- 1. t_farm_sow_record
  -- ============================================================
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_record' AND COLUMN_NAME='create_dept') THEN
    ALTER TABLE t_farm_sow_record ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门' AFTER tenant_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_record' AND COLUMN_NAME='create_by') THEN
    ALTER TABLE t_farm_sow_record ADD COLUMN create_by BIGINT NULL COMMENT '创建者' AFTER create_dept;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_record' AND COLUMN_NAME='update_by') THEN
    ALTER TABLE t_farm_sow_record ADD COLUMN update_by BIGINT NULL COMMENT '更新者' AFTER create_by;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_record' AND COLUMN_NAME='update_time') THEN
    ALTER TABLE t_farm_sow_record ADD COLUMN update_time DATETIME NULL COMMENT '更新时间' AFTER create_time;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_record' AND COLUMN_NAME='remark') THEN
    ALTER TABLE t_farm_sow_record ADD COLUMN remark VARCHAR(500) NULL COMMENT '备注' AFTER update_time;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_record' AND COLUMN_NAME='del_flag') THEN
    ALTER TABLE t_farm_sow_record ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志' AFTER remark;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_record' AND COLUMN_NAME='del_unique') THEN
    ALTER TABLE t_farm_sow_record ADD COLUMN del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删唯一标识' AFTER del_flag;
  END IF;
  -- 老 UNIQUE 不含 del_unique，软删后无法插同 tenant+date 新行 → DROP
  IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_record' AND INDEX_NAME='uk_tenant_date') THEN
    ALTER TABLE t_farm_sow_record DROP INDEX uk_tenant_date;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_record' AND INDEX_NAME='uk_sow_record_tenant_date') THEN
    ALTER TABLE t_farm_sow_record ADD UNIQUE KEY uk_sow_record_tenant_date (tenant_id, stat_date, del_unique);
  END IF;

  -- ============================================================
  -- 2. t_farm_sow_performance
  -- ============================================================
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_performance' AND COLUMN_NAME='create_dept') THEN
    ALTER TABLE t_farm_sow_performance ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门' AFTER tenant_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_performance' AND COLUMN_NAME='create_by') THEN
    ALTER TABLE t_farm_sow_performance ADD COLUMN create_by BIGINT NULL COMMENT '创建者' AFTER create_dept;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_performance' AND COLUMN_NAME='update_by') THEN
    ALTER TABLE t_farm_sow_performance ADD COLUMN update_by BIGINT NULL COMMENT '更新者' AFTER create_by;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_performance' AND COLUMN_NAME='update_time') THEN
    ALTER TABLE t_farm_sow_performance ADD COLUMN update_time DATETIME NULL COMMENT '更新时间' AFTER create_time;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_performance' AND COLUMN_NAME='remark') THEN
    ALTER TABLE t_farm_sow_performance ADD COLUMN remark VARCHAR(500) NULL COMMENT '备注' AFTER update_time;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_performance' AND COLUMN_NAME='del_flag') THEN
    ALTER TABLE t_farm_sow_performance ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志' AFTER remark;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_performance' AND COLUMN_NAME='del_unique') THEN
    ALTER TABLE t_farm_sow_performance ADD COLUMN del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删唯一标识' AFTER del_flag;
  END IF;
  IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_performance' AND INDEX_NAME='uk_tenant_pig') THEN
    ALTER TABLE t_farm_sow_performance DROP INDEX uk_tenant_pig;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_sow_performance' AND INDEX_NAME='uk_sow_perf_tenant_pig_parity') THEN
    ALTER TABLE t_farm_sow_performance ADD UNIQUE KEY uk_sow_perf_tenant_pig_parity (tenant_id, pig_id, parity, del_unique);
  END IF;

  -- ============================================================
  -- 3. t_farm_monthly_production
  -- ============================================================
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_monthly_production' AND COLUMN_NAME='create_dept') THEN
    ALTER TABLE t_farm_monthly_production ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门' AFTER tenant_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_monthly_production' AND COLUMN_NAME='create_by') THEN
    ALTER TABLE t_farm_monthly_production ADD COLUMN create_by BIGINT NULL COMMENT '创建者' AFTER create_dept;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_monthly_production' AND COLUMN_NAME='update_by') THEN
    ALTER TABLE t_farm_monthly_production ADD COLUMN update_by BIGINT NULL COMMENT '更新者' AFTER create_by;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_monthly_production' AND COLUMN_NAME='update_time') THEN
    ALTER TABLE t_farm_monthly_production ADD COLUMN update_time DATETIME NULL COMMENT '更新时间' AFTER create_time;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_monthly_production' AND COLUMN_NAME='remark') THEN
    ALTER TABLE t_farm_monthly_production ADD COLUMN remark VARCHAR(500) NULL COMMENT '备注' AFTER update_time;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_monthly_production' AND COLUMN_NAME='del_flag') THEN
    ALTER TABLE t_farm_monthly_production ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志' AFTER remark;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_monthly_production' AND COLUMN_NAME='del_unique') THEN
    ALTER TABLE t_farm_monthly_production ADD COLUMN del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删唯一标识' AFTER del_flag;
  END IF;
  IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_monthly_production' AND INDEX_NAME='uk_tenant_month') THEN
    ALTER TABLE t_farm_monthly_production DROP INDEX uk_tenant_month;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_monthly_production' AND INDEX_NAME='uk_monthly_tenant_month') THEN
    ALTER TABLE t_farm_monthly_production ADD UNIQUE KEY uk_monthly_tenant_month (tenant_id, stat_month, del_unique);
  END IF;

  -- ============================================================
  -- 4. t_farm_annual_indicator
  -- ============================================================
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_annual_indicator' AND COLUMN_NAME='create_dept') THEN
    ALTER TABLE t_farm_annual_indicator ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门' AFTER tenant_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_annual_indicator' AND COLUMN_NAME='create_by') THEN
    ALTER TABLE t_farm_annual_indicator ADD COLUMN create_by BIGINT NULL COMMENT '创建者' AFTER create_dept;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_annual_indicator' AND COLUMN_NAME='update_by') THEN
    ALTER TABLE t_farm_annual_indicator ADD COLUMN update_by BIGINT NULL COMMENT '更新者' AFTER create_by;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_annual_indicator' AND COLUMN_NAME='update_time') THEN
    ALTER TABLE t_farm_annual_indicator ADD COLUMN update_time DATETIME NULL COMMENT '更新时间' AFTER create_time;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_annual_indicator' AND COLUMN_NAME='remark') THEN
    ALTER TABLE t_farm_annual_indicator ADD COLUMN remark VARCHAR(500) NULL COMMENT '备注' AFTER update_time;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_annual_indicator' AND COLUMN_NAME='del_flag') THEN
    ALTER TABLE t_farm_annual_indicator ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志' AFTER remark;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_annual_indicator' AND COLUMN_NAME='del_unique') THEN
    ALTER TABLE t_farm_annual_indicator ADD COLUMN del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删唯一标识' AFTER del_flag;
  END IF;
  IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_annual_indicator' AND INDEX_NAME='uk_tenant_year') THEN
    ALTER TABLE t_farm_annual_indicator DROP INDEX uk_tenant_year;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='t_farm_annual_indicator' AND INDEX_NAME='uk_annual_tenant_year') THEN
    ALTER TABLE t_farm_annual_indicator ADD UNIQUE KEY uk_annual_tenant_year (tenant_id, stat_year, del_unique);
  END IF;
END$$

DELIMITER ;

CALL pr_brd_dash_001_align_audit();
DROP PROCEDURE pr_brd_dash_001_align_audit;
