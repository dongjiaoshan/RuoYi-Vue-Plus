-- 出栏操作员（EmployeePicker 所选员工 userId，snowflake string；与 operator_id 登录态审计列并存）
ALTER TABLE t_farm_pig_marketing ADD COLUMN operator VARCHAR(64) NULL COMMENT '出栏操作员 userId(EmployeePicker 选择)' AFTER operator_id;
