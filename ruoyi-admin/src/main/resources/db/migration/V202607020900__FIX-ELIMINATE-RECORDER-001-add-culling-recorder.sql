-- FIX-ELIMINATE-RECORDER-001：淘汰录入增加「淘汰记录人」字段（mp EmployeePicker 所选 sys_user.user_id）。
ALTER TABLE t_farm_pig_culling
  ADD COLUMN culling_recorder_id BIGINT NULL COMMENT '淘汰记录人 sys_user.user_id' AFTER operator_id;
