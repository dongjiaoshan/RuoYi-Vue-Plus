ALTER TABLE t_warehouse_pig_burn_record MODIFY COLUMN ear_no VARCHAR(32) NULL COMMENT '猪只耳号（自养填；外购无耳号留空，靠白条 supplier_id 区分）';
