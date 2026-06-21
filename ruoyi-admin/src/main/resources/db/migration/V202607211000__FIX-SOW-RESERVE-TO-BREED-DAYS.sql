-- ============================================================
-- 母猪生产配置新增「后备-配种天数」(sow_reserve_to_breed_days)
--
-- 后备母猪（HB）→ 配种 的建议天数。与现有 6 个 sow_* key 同族，
-- admin 母猪生产配置表单第 1 行展示；只决定建议时间，不自动改状态。
--
-- 业务键命名与现有 sow_*_to_*_days 一致；default_value 7（后备首配建议）。
-- seed 阶段 ruoyi 未起 handler，显式写 tenant_id；INSERT IGNORE 幂等。
-- ============================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO t_farm_production_cycle_config (
    id, tenant_id, config_key, default_value, custom_value, unit, description, create_by, create_time, del_flag, del_unique)
VALUES
  (108, '1001', 'sow_reserve_to_breed_days', 7, NULL, '天', '后备到配种天数', 1, NOW(), '0', 0);
