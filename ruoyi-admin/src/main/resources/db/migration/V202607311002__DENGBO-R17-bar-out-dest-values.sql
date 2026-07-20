-- row17（邓博 2026-07-02 澄清）：后台出库 = 矿山/厨房等直接来仓库拿货的出库去向。
-- 把 V202607311001 的占位去向（其他仓库/暂存冷库/损耗处理）替换为邓博实际语义（矿山/厨房/食堂/其他）。
-- 仍为可编辑字典数据，邓博确认最终去向清单后再增删。列集对齐 V202607311001（sys_dict_data 无 status 列）。
DELETE FROM sys_dict_data WHERE dict_type = 'djs_bar_out_dest';
INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
  (1007050, '1001', 1, '矿山', 'mine',    'djs_bar_out_dest', NULL, 'default', 'N', 1, NOW(), '后台出库去向-矿山（邓博示例）'),
  (1007051, '1001', 2, '厨房', 'kitchen', 'djs_bar_out_dest', NULL, 'default', 'N', 1, NOW(), '后台出库去向-厨房（邓博示例）'),
  (1007052, '1001', 3, '食堂', 'canteen', 'djs_bar_out_dest', NULL, 'default', 'N', 1, NOW(), '后台出库去向-食堂'),
  (1007053, '1001', 4, '其他', 'other',   'djs_bar_out_dest', NULL, 'default', 'N', 1, NOW(), '后台出库去向-其他');
