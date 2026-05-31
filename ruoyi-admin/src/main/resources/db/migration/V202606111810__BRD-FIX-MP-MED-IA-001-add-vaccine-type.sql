-- BRD-FIX-MP-MED-IA-001：用药记录加疫苗类型字段（107 用药治疗免疫类二级联动）
-- 字典 djs_vaccine_type（疫苗/菌苗/抗生素…8 类）；仅免疫类用药时填写
ALTER TABLE t_breed_medicine_record
  ADD COLUMN vaccine_type VARCHAR(32) NULL COMMENT '疫苗类型（字典 djs_vaccine_type，仅免疫类用药填写）' AFTER medicine_type;
