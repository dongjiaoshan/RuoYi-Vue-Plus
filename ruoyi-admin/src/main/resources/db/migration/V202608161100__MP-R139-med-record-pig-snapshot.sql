-- MP row139：用药提交时落猪只类型 + 母猪状态快照，供 row134 记录卡显示 + row138 猪只类型筛选
ALTER TABLE t_breed_medicine_record
  ADD COLUMN pig_type   VARCHAR(20) NULL COMMENT '用药时猪只类型快照(djs_pig_type)' AFTER ear_no,
  ADD COLUMN pig_status VARCHAR(20) NULL COMMENT '用药时母猪状态快照(djs_pig_lifecycle)' AFTER pig_type;
