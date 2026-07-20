-- 采摘人员（PickerPicker 所选班组成员 sys_user.user_id）；放 worker 字段段
ALTER TABLE t_plant_farm_records ADD COLUMN operator_user_id BIGINT NULL COMMENT '操作人(采摘人员)sys_user.user_id' AFTER farm_by;
