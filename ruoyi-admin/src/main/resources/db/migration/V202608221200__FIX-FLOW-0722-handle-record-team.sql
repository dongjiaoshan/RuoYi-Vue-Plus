-- ============================================================
-- FIX-FLOW-0722 row38/row39：毛菜处理采摘重量录入新增「采摘班组」
-- ============================================================
-- row38：mp 毛菜处理·采摘重量录入(record_type=1)新增必填「采摘班组」，选后记入 t_warehouse_handle_record.team_id。
-- row39：班组绩效统计口径改为以毛菜处理间采摘重量录入时各组对应作物称重重量为当月采收总重量
--   （聚合源从 t_plant_plant_details 改为 t_warehouse_handle_record，按 team_id×crop_id 汇总 record_weight）。
--   处理录入(record_type=2)不带班组，team_id 保持 NULL。
-- 幂等 guard（INFORMATION_SCHEMA 守卫）：可先手动 apply staging 做 live 验证，Flyway 部署重跑安全。
-- version 202608221200 > 当前 flyway max V202608221100。ADD COLUMN 无字典变更，无需 flush redis。
-- ============================================================
SET NAMES utf8mb4;
SET @db := DATABASE();

-- t_warehouse_handle_record.team_id 列（采摘班组 FK t_plant_work_team.id，幂等）
SET @has_team := (SELECT COUNT(*) FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_warehouse_handle_record' AND COLUMN_NAME='team_id');
SET @sql := IF(@has_team=0,
  "ALTER TABLE t_warehouse_handle_record ADD COLUMN team_id BIGINT NULL COMMENT '采摘班组 FK t_plant_work_team.id（record_type=1 采收录入必填；record_type=2 处理录入为 NULL）' AFTER crop_id",
  "DO 0");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
