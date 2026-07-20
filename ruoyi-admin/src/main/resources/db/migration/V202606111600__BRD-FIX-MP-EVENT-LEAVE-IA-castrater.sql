-- BRD-FIX-MP-EVENT-LEAVE-IA-001：阉割记录补「阉割人员」列（原型 87 字段）。
--
-- castrater 是现场实际执刀人员（名/编码），与 operator_id（系统登录态录入账号）并存。
-- V1 mp 端无员工 picker applet 端点，退化为手填字符串落库；V1.x 接员工下拉后回填同列。
ALTER TABLE t_farm_castrate_record
  ADD COLUMN castrater VARCHAR(64) NULL COMMENT '阉割人员（现场执刀人员名/编码）' AFTER operator_id;
