-- DENGBO-R30 药品领用：领用日期下新增「领用人」。
-- t_breed_medicine_usage 加 operator_id + operator_name（默认当前登录人，可选其他员工，ADR-0007）。
ALTER TABLE t_breed_medicine_usage
    ADD COLUMN operator_id   BIGINT       NULL COMMENT '领用人 user_id（默认当前登录人）' AFTER schedule_id,
    ADD COLUMN operator_name VARCHAR(64)  NULL COMMENT '领用人姓名快照' AFTER operator_id;
