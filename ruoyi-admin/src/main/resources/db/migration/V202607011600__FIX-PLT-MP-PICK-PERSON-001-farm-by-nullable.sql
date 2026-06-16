-- FIX-PLT-MP-PICK-PERSON-001：放开 t_plant_farm_records.farm_by 允许 NULL。
-- 采摘录入改走「采摘人员」个人维度（落 operator_user_id），不再强制归属班组；
-- 个人录入时 farm_by 置空，班组录入仍写 farm_by。
ALTER TABLE t_plant_farm_records
    MODIFY COLUMN farm_by BIGINT NULL COMMENT '作业班组ID（采摘个人录入时可空，个人记录在 operator_user_id）';
