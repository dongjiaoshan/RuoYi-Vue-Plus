-- admin row162：后备种母猪「转为育肥猪」
--   1) 新增猪只生命周期状态 YF「育肥」——后备(HB) 种母猪转育肥后落此态，事件台账据此显示状态流转。
--   2) 新增状态机事件 TO_FATTEN「转育肥」——事件台账 / 状态记录按此事件筛。
-- dict_code 取 1001900+ 空闲段（1001030-1001050 段的下一位已被 djs_barn_type 等占用，
-- 现存 1001xxx 最大 1001511；1001900-1001999 全空）；INSERT IGNORE 保证重跑幂等。

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
  (1001900, '1001', 10, '育肥', 'YF', 'djs_pig_lifecycle', '', 'warning', 'N', NULL, NOW(), '后备种母猪转育肥后的状态（admin row162）');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
  (1001901, '1001', 11, '转育肥', 'TO_FATTEN', 'djs_pig_status_event', '', 'warning', 'N', NULL, NOW(), '后备种母猪转为育肥猪（admin row162）');
