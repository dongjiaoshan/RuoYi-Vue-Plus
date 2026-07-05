-- row35/36：用药记录新增剂量单位（纯记录/展示，不参与库存扣减）
ALTER TABLE t_breed_medicine_record ADD COLUMN dosage_unit VARCHAR(32) NULL COMMENT '剂量单位（记录展示用：g/mg/ml/L/kg/片等）' AFTER medicine_dosage;
