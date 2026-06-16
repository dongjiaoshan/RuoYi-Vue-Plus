-- FIX-6-16-DIE-RECORDER-001：死亡录入页新增「死亡记录人员」字段。
-- mp 死亡录入页用 EmployeePicker 选记录人员，落 t_farm_pig_death.recorder_id（sys_user.user_id）。
-- 记录列表 VO 经 USER_ID_TO_NICKNAME 翻译显名。
ALTER TABLE t_farm_pig_death
    ADD COLUMN recorder_id BIGINT NULL COMMENT '死亡记录人员ID（sys_user.user_id）' AFTER operator_id;
