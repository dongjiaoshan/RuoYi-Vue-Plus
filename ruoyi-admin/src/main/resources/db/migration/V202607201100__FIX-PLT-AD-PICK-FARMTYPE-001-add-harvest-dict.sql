-- FIX-PLT-AD-PICK-FARMTYPE-001：新增「采收」农事类型字典（djs_farm_work_type）。
-- 口径：采摘计划里设了「采摘活动」(is_pick=1) 的采摘行为记 harvest_activity（采摘活动）；
--       其它普通采收 (is_pick=2) 记 harvest（采收）。区别于游客采摘活动，作物详情农事信息 tag 区分显示。
INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1026012, '1001', 12, '采收', 'harvest', 'djs_farm_work_type', '', 'primary', 'N', 1, NOW(), '普通采收 is_pick=2，区别于游客采摘活动 harvest_activity');
