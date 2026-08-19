-- V6 row111：猪肉追溯码新增「排酸时长」字段，出库时间写入时一并定格；C 端时间线按它判断要不要显示排酸时间点。
-- 时长来源 cut_record.acid_remove_minutes（分割 / 发货月台 / 后台出库三种出库方式都写它；
-- bar_info.acid_remove_time 只有分割路径写，用它会整片缺值）。
ALTER TABLE t_warehouse_trace_code
    ADD COLUMN acid_remove_minutes INT NULL COMMENT '排酸时长（分钟，猪肉链；出库时定格）' AFTER pig_ear_no;

-- 排酸时长控制字典：单条项，dict_value 即分钟数下限。低于它的不在追溯页显示排酸时间点。
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (102608190, '1001', '猪肉排酸时长控制', 'djs_pork_acid_control', NULL, NOW(),
   '追溯页排酸节点展示阈值：dict_value 即分钟数，排酸时长低于它则不显示排酸时间点');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1026081900, '1001', 0, '排酸时长下限（分钟）', '60', 'djs_pork_acid_control', '', 'primary', 'Y', NULL, NOW());

-- 存量猪肉码回填排酸时长：按耳号找该猪最新一条带排酸时长的出库记录。
UPDATE t_warehouse_trace_code tc
   JOIN (SELECT b.ear_no,
                SUBSTRING_INDEX(GROUP_CONCAT(r.acid_remove_minutes ORDER BY r.id DESC), ',', 1) AS minutes
           FROM t_warehouse_pig_cut_record r
           JOIN t_warehouse_bar_info b ON b.id = r.white_bar_id
          WHERE r.acid_remove_minutes IS NOT NULL
            AND b.ear_no IS NOT NULL
          GROUP BY b.ear_no) m
     ON m.ear_no = tc.pig_ear_no
    SET tc.acid_remove_minutes = CAST(m.minutes AS UNSIGNED)
  WHERE tc.del_flag = '0'
    AND tc.code_type = 'pork'
    AND tc.acid_remove_minutes IS NULL;
