-- BRD-WEAN-SELECT-001（V6 行239①）：断奶逐头明细补母猪耳号列。
-- 断奶从「整窝一起断」改为「按所选仔猪断」后，同一头母猪同一窝可能产生多条断奶记录，
-- 明细行只有 weaning_id 时看不出这头仔猪断的是哪头母猪；冗余母猪耳号快照，导出 / 排查直接可读。
-- 值取自断奶主记录 t_farm_pig_weaning.ear_no（落库时冻结，母猪后续改耳号不追溯）。

ALTER TABLE t_farm_pig_weaning_detail
  ADD COLUMN sow_ear_no VARCHAR(32) NULL COMMENT '母猪耳号（取自 t_farm_pig_weaning.ear_no 快照）' AFTER weaning_id;

-- 存量回填：按 weaning_id 反查主记录母猪耳号（含已软删主记录，明细留底保持可读）
UPDATE t_farm_pig_weaning_detail d
  JOIN t_farm_pig_weaning w
    ON w.id = d.weaning_id
   AND w.tenant_id = d.tenant_id
   SET d.sow_ear_no = w.ear_no
 WHERE d.sow_ear_no IS NULL;
